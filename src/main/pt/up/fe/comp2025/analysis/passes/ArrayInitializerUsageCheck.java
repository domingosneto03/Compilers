package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;

public class ArrayInitializerUsageCheck extends AnalysisVisitor {

    private String currentMethod;
    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        this.currentMethod = methodDecl.get("name").equals("args") ? "main" : methodDecl.get("name");
        this.typeUtils = new TypeUtils(table);
        return null;
    }

    private Void visitAssignStmt(JmmNode node, SymbolTable table) {
        if (node.getNumChildren() != 2) return null;

        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);

        if (!Kind.VAR_REF_EXPR.check(left)) {
            return null;
        }

        String varName = left.get("value");
        Type expected = null;

        List<Symbol> locals = table.getLocalVariables(currentMethod);
        List<Symbol> params = table.getParameters(currentMethod);
        List<Symbol> fields = table.getFields();

        for (Symbol s : locals) {
            if (s.getName().equals(varName)) {
                expected = s.getType();
                break;
            }
        }
        if (expected == null) {
            for (Symbol s : params) {
                if (s.getName().equals(varName)) {
                    expected = s.getType();
                    break;
                }
            }
        }
        if (expected == null) {
            for (Symbol s : fields) {
                if (s.getName().equals(varName)) {
                    expected = s.getType();
                    break;
                }
            }
        }

        if (expected == null) {
            return null;
        }

        try {
            Type actual = typeUtils.getExprType(right, currentMethod);
            checkArrayInitializerUsage(right, expected, actual, node);
        } catch (RuntimeException e) {
            addReport(Report.newError(Stage.SEMANTIC, node.getLine(), node.getColumn(),
                    "Failed to evaluate assignment RHS: " + e.getMessage(), null));
        }

        return null;
    }

    private Void visitReturnStmt(JmmNode node, SymbolTable table) {
        if (node.getNumChildren() == 0) return null;

        JmmNode expr = node.getChild(0);

        try {
            Type actual = typeUtils.getExprType(expr, currentMethod);
            Type expected = table.getReturnType(currentMethod);

            checkArrayInitializerUsage(expr, expected, actual, node);

            if (!"unknown".equals(actual.getName()) &&
                    (!expected.getName().equals(actual.getName()) || expected.isArray() != actual.isArray())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        String.format("Return type mismatch in method '%s': expected %s, but found %s",
                                currentMethod, expected, actual),
                        null
                ));
            }
        } catch (RuntimeException e) {
            addReport(Report.newError(Stage.SEMANTIC, node.getLine(), node.getColumn(),
                    "Failed to evaluate return expression: " + e.getMessage(), null));
        }

        return null;
    }

    private Void visitMethodCallExpr(JmmNode node, SymbolTable table) {
        if (node.getNumChildren() < 1) return null;

        String methodName = node.get("method");

        if (!table.getMethods().contains(methodName)) {
            return null;
        }

        var expectedParams = table.getParameters(methodName);
        if (expectedParams == null) return null;

        for (int i = 1; i < node.getNumChildren(); i++) {
            JmmNode arg = node.getChild(i);

            try {
                Type actual = typeUtils.getExprType(arg, currentMethod);

                if (i - 1 < expectedParams.size()) {
                    Type expected = expectedParams.get(i - 1).getType();
                    checkArrayInitializerUsage(arg, expected, actual, node);
                }
            } catch (RuntimeException e) {
                addReport(Report.newError(Stage.SEMANTIC, node.getLine(), node.getColumn(),
                        "Failed to evaluate method call argument: " + e.getMessage(), null));
            }
        }

        return null;
    }

    private void checkArrayInitializerUsage(JmmNode node, Type expected, Type actual, JmmNode reportNode) {
        if (Kind.ARRAY_LITERAL_EXPR.check(node) &&
                (!expected.getName().equals("int") || !expected.isArray())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    reportNode.getLine(),
                    reportNode.getColumn(),
                    String.format("Array initializer cannot be used here. Expected: %s, but got: %s", expected, actual),
                    null
            ));
        }
    }
}
