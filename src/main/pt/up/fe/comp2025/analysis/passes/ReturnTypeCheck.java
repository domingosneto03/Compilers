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
 * Verifica se o tipo de retorno dos métodos está correto.
 * - Verifica se o tipo da expressão de retorno é compatível com o tipo declarado do método
 * - Verifica se métodos void não retornam valores
 * - Verifica se métodos não-void têm statements de retorno
 */
public class ReturnTypeCheck extends AnalysisVisitor {

    private String currentMethod;
    private TypeUtils typeUtils;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        this.typeUtils = new TypeUtils(table);

        Type returnType = table.getReturnType(currentMethod);
        if (returnType != null && !returnType.getName().equals("void")) {
            boolean hasReturn = hasReturnStatement(methodDecl);
            if (!hasReturn) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        methodDecl.getLine(),
                        methodDecl.getColumn(),
                        String.format("Method '%s' must return a value of type %s", currentMethod, returnType),
                        null
                ));
            }
        }

        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        Type expectedReturnType = table.getReturnType(currentMethod);

        if (expectedReturnType == null) {
            return null;
        }

        boolean hasExpression = returnStmt.getNumChildren() > 0;

        if (expectedReturnType.getName().equals("void")) {
            if (hasExpression) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        returnStmt.getLine(),
                        returnStmt.getColumn(),
                        String.format("Method '%s' is void but returns a value", currentMethod),
                        null
                ));
            }
        } else {
            if (!hasExpression) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        returnStmt.getLine(),
                        returnStmt.getColumn(),
                        String.format("Method '%s' must return a value of type %s", currentMethod, expectedReturnType),
                        null
                ));
                return null;
            }

            // Verificar tipo da expressão de retorno
            JmmNode returnExpr = returnStmt.getChild(0);
            try {
                Type actualReturnType = typeUtils.getExprType(returnExpr, currentMethod);

                if (!isTypeCompatible(expectedReturnType, actualReturnType, table)) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            returnStmt.getLine(),
                            returnStmt.getColumn(),
                            String.format("Return type mismatch in method '%s': expected %s, but found %s",
                                    currentMethod, expectedReturnType, actualReturnType),
                            null
                    ));
                }
            } catch (RuntimeException e) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        returnStmt.getLine(),
                        returnStmt.getColumn(),
                        "Failed to evaluate return expression: " + e.getMessage(),
                        null
                ));
            }
        }

        return null;
    }

    private boolean hasReturnStatement(JmmNode methodDecl) {
        return hasReturnStatementRecursive(methodDecl);
    }

    private boolean hasReturnStatementRecursive(JmmNode node) {
        if (node.getKind().equals(Kind.RETURN_STMT.getNodeName())) {
            return true;
        }

        for (JmmNode child : node.getChildren()) {
            if (hasReturnStatementRecursive(child)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica se dois tipos são compatíveis para atribuição.
     * Considera herança e imports.
     */
    private boolean isTypeCompatible(Type expected, Type actual, SymbolTable table) {
        // Tipos primitivos devem ser exatamente iguais
        if (isPrimitiveType(expected) && isPrimitiveType(actual)) {
            return expected.getName().equals(actual.getName()) &&
                    expected.isArray() == actual.isArray();
        }

        // Se o tipo atual é "unknown" (de imports), assumir compatível
        if ("unknown".equals(actual.getName())) {
            return true;
        }

        // Arrays: tipos base devem ser compatíveis
        if (expected.isArray() || actual.isArray()) {
            return expected.getName().equals(actual.getName()) &&
                    expected.isArray() == actual.isArray();
        }

        // Tipos de objeto
        if (expected.getName().equals(actual.getName())) {
            return true;
        }

        // Verificar herança: se actual extends expected
        String actualClassName = actual.getName();
        String expectedClassName = expected.getName();
        String currentClass = table.getClassName();
        String superClass = table.getSuper();

        // Se actual é a classe atual e expected é a superclasse
        if (actualClassName.equals(currentClass) && expectedClassName.equals(superClass)) {
            return true;
        }

        // Se ambos são classes importadas, assumir compatível
        boolean actualIsImported = table.getImports().stream()
                .anyMatch(imp -> imp.endsWith("." + actualClassName) || imp.equals(actualClassName));
        boolean expectedIsImported = table.getImports().stream()
                .anyMatch(imp -> imp.endsWith("." + expectedClassName) || imp.equals(expectedClassName));

        if (actualIsImported && expectedIsImported) {
            return true;
        }

        return false;
    }

    private boolean isPrimitiveType(Type type) {
        String name = type.getName();
        return "int".equals(name) || "boolean".equals(name) || "String".equals(name) || "void".equals(name);
    }
}
