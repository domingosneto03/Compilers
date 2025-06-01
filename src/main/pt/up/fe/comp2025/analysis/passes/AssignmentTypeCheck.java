package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;

public class AssignmentTypeCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(pt.up.fe.comp2025.ast.Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(pt.up.fe.comp2025.ast.Kind.ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        //System.out.println("[DEBUG] AssignmentTypeCheck — entering method: " + currentMethod);
        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        int numChildren = assignStmt.getNumChildren();
        Type leftType = null;
        Type rightType = null;
        TypeUtils typeUtils = new TypeUtils(table);

        if (numChildren == 2) {
            // Simple assignment: a = expr;
            JmmNode lhs = assignStmt.getChild(0);
            JmmNode rhs = assignStmt.getChild(1);

            if (!lhs.hasAttribute("value")) {
                addReport(Report.newError(Stage.SEMANTIC, lhs.getLine(), lhs.getColumn(),
                        "Left-hand side of assignment does not contain a valid variable reference", null));
                return null;
            }

            String varName = lhs.get("value");
            leftType = lookupVariableType(varName, table, currentMethod);
            if (leftType == null) {
                addReport(Report.newError(Stage.SEMANTIC, lhs.getLine(), lhs.getColumn(),
                        "Undeclared variable in assignment: " + varName, null));
                return null;
            }

            try {
                rightType = typeUtils.getExprType(rhs, currentMethod);
                if (rightType == null) {
                    addReport(Report.newError(Stage.SEMANTIC, rhs.getLine(), rhs.getColumn(),
                            "Could not determine type of right-hand side expression", null));
                    return null;
                }
            } catch (RuntimeException e) {
                addReport(Report.newError(Stage.SEMANTIC, rhs.getLine(), rhs.getColumn(),
                        "Failed to evaluate assignment RHS: " + e.getMessage(), null));
                return null;
            }

        } else if (numChildren == 3) {
            // Array assignment: a[i] = expr;
            JmmNode arrayExpr = assignStmt.getChild(0);
            JmmNode indexExpr = assignStmt.getChild(1);
            JmmNode rhs = assignStmt.getChild(2);

            try {
                Type arrayType = typeUtils.getExprType(arrayExpr, currentMethod);
                if (arrayType == null) {
                    addReport(Report.newError(Stage.SEMANTIC, arrayExpr.getLine(), arrayExpr.getColumn(),
                            "Could not determine type of array expression", null));
                    return null;
                }
                
                if (!arrayType.isArray()) {
                    addReport(Report.newError(Stage.SEMANTIC, arrayExpr.getLine(), arrayExpr.getColumn(),
                            "Left-hand side is not an array, but used as one.", null));
                    return null;
                }

                // Verify index is int
                Type indexType = typeUtils.getExprType(indexExpr, currentMethod);
                if (indexType == null || !"int".equals(indexType.getName()) || indexType.isArray()) {
                    String actualIndexType = indexType != null ? 
                        (indexType.isArray() ? indexType.getName() + "[]" : indexType.getName()) : 
                        "unknown";
                    addReport(Report.newError(Stage.SEMANTIC, indexExpr.getLine(), indexExpr.getColumn(),
                            "Array index must be int, but found: " + actualIndexType, null));
                    return null;
                }

                leftType = new Type(arrayType.getName(), false); // Element type
                rightType = typeUtils.getExprType(rhs, currentMethod);
                
                if (rightType == null) {
                    addReport(Report.newError(Stage.SEMANTIC, rhs.getLine(), rhs.getColumn(),
                            "Could not determine type of right-hand side expression", null));
                    return null;
                }

            } catch (RuntimeException e) {
                addReport(Report.newError(Stage.SEMANTIC, assignStmt.getLine(), assignStmt.getColumn(),
                        "Failed to evaluate array assignment: " + e.getMessage(), null));
                return null;
            }

        } else {
            return null; // Skip malformed assignments
        }

        // Strict type compatibility check
        if (!isTypeCompatible(leftType, rightType, table)) {
            String leftTypeStr = leftType.isArray() ? leftType.getName() + "[]" : leftType.getName();
            String rightTypeStr = rightType.isArray() ? rightType.getName() + "[]" : rightType.getName();
            String message = String.format("Type mismatch in assignment: cannot assign %s to %s", rightTypeStr, leftTypeStr);
            addReport(Report.newError(Stage.SEMANTIC, assignStmt.getLine(), assignStmt.getColumn(), message, null));
        }

        return null;
    }


    private Type lookupVariableType(String varName, SymbolTable table, String currentMethod) {
        for (Symbol symbol : table.getLocalVariables(currentMethod)) {
            if (symbol.getName().equals(varName)) {
                return symbol.getType();
            }
        }
        for (Symbol symbol : table.getParameters(currentMethod)) {
            if (symbol.getName().equals(varName)) {
                return symbol.getType();
            }
        }
        for (Symbol symbol : table.getFields()) {
            if (symbol.getName().equals(varName)) {
                return symbol.getType();
            }
        }
        return null;
    }

    private boolean isTypeCompatible(Type left, Type right, SymbolTable table) {
        // Null types are never compatible
        if (left == null || right == null) {
            return false;
        }

        // Array types: both array flags and base types must match exactly
        if (left.isArray() || right.isArray()) {
            return left.getName().equals(right.getName()) && (left.isArray() == right.isArray());
        }

        // Primitive types: must match exactly (no implicit conversions)
        if (isPrimitiveType(left) || isPrimitiveType(right)) {
            return left.getName().equals(right.getName()) && left.isArray() == right.isArray();
        }

        // Special case: if right type is "unknown" (imported method), assume compatibility
        if ("unknown".equals(right.getName())) {
            return true; // Allow assignment from unknown imported method calls
        }

        // Object types: exact match
        if (left.getName().equals(right.getName())) {
            return true;
        }

        // Inheritance: check if right type extends left type
        if (table instanceof JmmSymbolTable jmmTable) {
            String currentClass = jmmTable.getClassName();
            String superClass = jmmTable.getSuper();
            
            // If right is current class and left is superclass
            if (right.getName().equals(currentClass) && superClass != null && superClass.equals(left.getName())) {
                return true;
            }
        }

        // Check if both types are imported (assume compatible for imported types)
        boolean leftIsImported = table.getImports().stream()
                .anyMatch(imp -> imp.endsWith("." + left.getName()) || imp.equals(left.getName()));
        boolean rightIsImported = table.getImports().stream()
                .anyMatch(imp -> imp.endsWith("." + right.getName()) || imp.equals(right.getName()));
        
        if (leftIsImported && rightIsImported) {
            return true; // Assume imported types can be compatible
        }

        // No compatibility found
        return false;
    }

    private boolean isPrimitiveType(Type type) {
        String typeName = type.getName();
        return "int".equals(typeName) || "boolean".equals(typeName) || "void".equals(typeName);
    }
}
