package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

/**
 * Combined check for array access expressions.
 * It verifies that:
 *   1. The base expression is an array.
 *   2. The index expression is of type int.
 *
 * This pass first checks if the array access node is complete (i.e. has at least 2 children)
 * to avoid false errors on incomplete assignment expressions.
 */
public class ArrayAccessCombinedCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        // Record the current method for symbol lookup.
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        // Visit array access expressions.
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.ARRAY_ASSIGN, this::visitArrayAssign);

    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        return null;
    }

    private Void visitArrayAccessExpr(JmmNode arrayAccessExpr, SymbolTable table) {
        if (arrayAccessExpr.getNumChildren() < 2) {
            return null;
        }

        TypeUtils typeUtils = new TypeUtils(table);

        // Check that the base expression (first child) is an array.
        JmmNode arrayExpr = arrayAccessExpr.getChild(0);
        Type arrayType = typeUtils.getExprType(arrayExpr, currentMethod);
        if (!arrayType.isArray()) {
            String message = String.format("Array access on non-array type: %s.", arrayType);
            addReport(Report.newError(Stage.SEMANTIC,
                    arrayAccessExpr.getLine(),
                    arrayAccessExpr.getColumn(),
                    message,
                    null));
        }

        // Check that the index expression (second child) is of type int.
        JmmNode indexExpr = arrayAccessExpr.getChild(1);
        Type indexType = typeUtils.getExprType(indexExpr, currentMethod);
        if (!"int".equals(indexType.getName()) || indexType.isArray()) {
            String message = String.format("Array index must be an expression of type int, but found: %s.", indexType);
            addReport(Report.newError(Stage.SEMANTIC,
                    arrayAccessExpr.getLine(),
                    arrayAccessExpr.getColumn(),
                    message,
                    null));
        }

        if ("IntegerLiteral".equals(indexExpr.getKind())) {
            String valueStr = indexExpr.get("value");
            try {
                int value = Integer.parseInt(valueStr);
                if (value < 0) {
                    String message = "Array index must be a non-negative integer literal, found: " + value;
                    addReport(Report.newError(Stage.SEMANTIC,
                            indexExpr.getLine(),
                            indexExpr.getColumn(),
                            message,
                            null));
                }
            } catch (NumberFormatException e) {
                String message = "Malformed integer literal in array index: " + valueStr;
                addReport(Report.newError(Stage.SEMANTIC,
                        indexExpr.getLine(),
                        indexExpr.getColumn(),
                        message,
                        null));
            }
        }

        return null;
    }
    private Void visitArrayAssign(JmmNode arrayAssign, SymbolTable table) {
        if (arrayAssign.getNumChildren() < 3) return null;

        TypeUtils typeUtils = new TypeUtils(table);

        JmmNode arrayExpr = arrayAssign.getChild(0);
        JmmNode indexExpr = arrayAssign.getChild(1);
        JmmNode valueExpr = arrayAssign.getChild(2);

        Type arrayType = typeUtils.getExprType(arrayExpr, currentMethod);
        Type indexType = typeUtils.getExprType(indexExpr, currentMethod);
        Type valueType = typeUtils.getExprType(valueExpr, currentMethod);

        // Verificar se arrayExpr é mesmo um array
        if (!arrayType.isArray()) {
            String message = String.format("Trying to assign to non-array type: %s.", arrayType);
            addReport(Report.newError(Stage.SEMANTIC,
                    arrayExpr.getLine(), arrayExpr.getColumn(), message, null));
        }

        // Verificar se o índice é int
        if (!indexType.getName().equals("int") || indexType.isArray()) {
            String message = String.format("Array index must be of type int, but got: %s.", indexType);
            addReport(Report.newError(Stage.SEMANTIC,
                    indexExpr.getLine(), indexExpr.getColumn(), message, null));
        }

        // Verificar se o tipo do valor atribuído é compatível com o tipo base do array
        if (arrayType.isArray() && (!valueType.getName().equals(arrayType.getName()) || valueType.isArray())) {
            String message = String.format("Type mismatch: cannot assign %s to element of array of %s.",
                    valueType, arrayType.getName());
            addReport(Report.newError(Stage.SEMANTIC,
                    valueExpr.getLine(), valueExpr.getColumn(), message, null));
        }

        return null;
    }

}