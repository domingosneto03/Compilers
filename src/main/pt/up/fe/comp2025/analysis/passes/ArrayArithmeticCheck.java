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
 * This pass checks that arithmetic operators (e.g. +, -, *, /)
 * are not applied to array types.
 *
 * If an arithmetic operator is applied to an operand that is an array,
 * a semantic error is reported.
 */
public class ArrayArithmeticCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        // Record current method context.
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        // Visit binary expressions.
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        String op = binaryExpr.get("op");
        // Check only arithmetic operators.
        if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op)) {
            TypeUtils typeUtils = new TypeUtils(table);
            // Get the left and right operand types.
            Type leftType = typeUtils.getExprType(binaryExpr.getChild(0), currentMethod);
            Type rightType = typeUtils.getExprType(binaryExpr.getChild(1), currentMethod);

            // If either operand is an array, report an error.
            if (leftType.isArray() || rightType.isArray()) {
                String message = String.format(
                        "Arithmetic operator '%s' cannot be applied to array types (%s and %s).",
                        op, leftType, rightType);
                addReport(Report.newError(Stage.SEMANTIC,
                        binaryExpr.getLine(),
                        binaryExpr.getColumn(),
                        message,
                        null));
            }
        }
        return null;
    }
}
