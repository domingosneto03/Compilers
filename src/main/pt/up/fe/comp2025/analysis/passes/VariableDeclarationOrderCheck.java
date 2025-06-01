package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.*;

/**
 * Checks for variables that are used before they are declared within method scope.
 * This implements the "declaração a meio" semantic check - variables declared after their first usage.
 */
public class VariableDeclarationOrderCheck extends AnalysisVisitor {

    private String currentMethod;
    private Map<String, Integer> variableDeclarationLines;
    private Map<String, Integer> variableFirstUsageLines;
    
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
    }
    
    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        // Initialize tracking for this method
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        variableDeclarationLines = new HashMap<>();
        variableFirstUsageLines = new HashMap<>();
        
        return null;
    }
    
    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        if (currentMethod == null) return null;
        
        String varName = varDecl.get("name");
        int declarationLine = varDecl.getLine();
        
        // Record the declaration line for this variable
        variableDeclarationLines.put(varName, declarationLine);
        
        // Check if this variable was used before being declared
        if (variableFirstUsageLines.containsKey(varName)) {
            int firstUsageLine = variableFirstUsageLines.get(varName);
            if (firstUsageLine < declarationLine) {
                addReport(Report.newError(
                    Stage.SEMANTIC,
                    declarationLine,
                    varDecl.getColumn(),
                    String.format("Variable '%s' is declared at line %d but was already used at line %d. Variables must be declared before use.",
                            varName, declarationLine, firstUsageLine),
                    null
                ));
            }
        }
        
        return null;
    }
    
    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        if (currentMethod == null) return null;
        
        String varName = varRefExpr.get("value");
        int usageLine = varRefExpr.getLine();
        
        // Skip if this is a parameter, field, or imported class
        if (isParameterFieldOrImport(varName, table)) {
            return null;
        }
        
        // Record first usage if not already recorded
        if (!variableFirstUsageLines.containsKey(varName)) {
            variableFirstUsageLines.put(varName, usageLine);
        }
        
        // If variable is already declared, check if usage comes after declaration
        if (variableDeclarationLines.containsKey(varName)) {
            int declarationLine = variableDeclarationLines.get(varName);
            if (usageLine < declarationLine) {
                addReport(Report.newError(
                    Stage.SEMANTIC,
                    usageLine,
                    varRefExpr.getColumn(),
                    String.format("Variable '%s' is used at line %d but declared later at line %d. Variables must be declared before use.",
                            varName, usageLine, declarationLine),
                    null
                ));
            }
        }
        
        return null;
    }
    
    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        if (currentMethod == null) return null;
        
        // Handle left-hand side of assignment (could be variable usage)
        JmmNode lhs = assignStmt.getChild(0);
        if (Kind.VAR_REF_EXPR.check(lhs)) {
            String varName = lhs.get("value");
            int usageLine = lhs.getLine();
            
            // Skip if this is a parameter, field, or imported class
            if (isParameterFieldOrImport(varName, table)) {
                return null;
            }
            
            // Record first usage if not already recorded
            if (!variableFirstUsageLines.containsKey(varName)) {
                variableFirstUsageLines.put(varName, usageLine);
            }
            
            // If variable is already declared, check if usage comes after declaration
            if (variableDeclarationLines.containsKey(varName)) {
                int declarationLine = variableDeclarationLines.get(varName);
                if (usageLine < declarationLine) {
                    addReport(Report.newError(
                        Stage.SEMANTIC,
                        usageLine,
                        lhs.getColumn(),
                        String.format("Variable '%s' is assigned at line %d but declared later at line %d. Variables must be declared before use.",
                                varName, usageLine, declarationLine),
                        null
                    ));
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a variable name corresponds to a method parameter, class field, or imported class
     */
    private boolean isParameterFieldOrImport(String varName, SymbolTable table) {
        // Check if it's a method parameter
        List<pt.up.fe.comp.jmm.analysis.table.Symbol> parameters = table.getParameters(currentMethod);
        if (parameters != null && parameters.stream()
                .anyMatch(param -> param.getName().equals(varName))) {
            return true;
        }
        
        // Check if it's a class field
        List<pt.up.fe.comp.jmm.analysis.table.Symbol> fields = table.getFields();
        if (fields != null && fields.stream()
                .anyMatch(field -> field.getName().equals(varName))) {
            return true;
        }
        
        // Check if it matches an imported class
        List<String> imports = table.getImports();
        if (imports != null && imports.stream().anyMatch(importStr -> {
            int lastDot = importStr.lastIndexOf('.');
            String simpleName = (lastDot >= 0) ? importStr.substring(lastDot + 1) : importStr;
            return simpleName.equals(varName);
        })) {
            return true;
        }
        
        return false;
    }
}
