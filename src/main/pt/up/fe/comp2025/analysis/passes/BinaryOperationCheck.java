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
 * Checks binary arithmetic operations for type compatibility.
 * In particular, it ensures that arithmetic operators (such as '*', '/', '+' and '-')
 * are applied to operands of type int. If not, a semantic error is reported.
 */
public class BinaryOperationCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        // Record the current method name from a METHOD_DECL node.
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        // Visit all binary expression nodes.
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        // Retrieve the operator attribute (e.g. "*", "/", "+" or "-")
        String op = binaryExpr.get("op");
        // If the operator is one of the arithmetic ones, perform the type check.
        if ("*".equals(op) || "/".equals(op) || "+".equals(op) || "-".equals(op)) {
            // Instantiate TypeUtils with the symbol table.
            TypeUtils typeUtils = new TypeUtils(table);
            JmmNode leftOperand = binaryExpr.getChild(0);
            JmmNode rightOperand = binaryExpr.getChild(1);
            Type leftType = typeUtils.getExprType(leftOperand, currentMethod);
            Type rightType = typeUtils.getExprType(rightOperand, currentMethod);
            if (!"int".equals(leftType.getName()) || leftType.isArray()
                    || !"int".equals(rightType.getName()) || rightType.isArray()) {
                String message = String.format("Operator '%s' expects integer operands, but found '%s' and '%s'.",
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
