package pt.up.fe.comp2025.analysis.passes;

import java.util.ArrayList;
import java.util.List;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import static pt.up.fe.comp2025.ast.Kind.METHOD_CALL_EXPR;
import static pt.up.fe.comp2025.ast.Kind.METHOD_DECL;

/**
 * Verifies that:
 * 1) Only methods declared in this class get fully checked.
 * 2) Number of args matches signature (accounting for varargs).
 * 3) Each argument’s type matches the formal parameter.
 */
public class MethodVerificationVisitor extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCall);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        currentMethod = methodDecl.get("name");
        return null;
    }

    private Void visitMethodCall(JmmNode callNode, SymbolTable table) {
        String mName = callNode.get("method");

        // ←— PRIMEIRA MUDANÇA: só valida se estiver na lista de métodos desta classe
        if (!table.getMethods().contains(mName)) {
            return null; // skip calls "assumidos" por extends/import
        }

        // agora faz lookup dos parâmetros formais
        List<Symbol> formals = table.getParameters(mName);
        if (formals == null) {
            addReport(error(callNode, "Método não declarado: '" + mName + "'"));
            return null;
        }

        // extrai apenas os nós de argumento (ignora o receiver)
        List<JmmNode> args = new ArrayList<>(callNode.getChildren().subList(1, callNode.getChildren().size()));

        int pCount    = formals.size();
        boolean isVar = pCount > 0 && formals.get(pCount - 1).getType().isArray();
        int minArgs   = isVar ? pCount - 1 : pCount;
        int maxArgs   = isVar ? Integer.MAX_VALUE : pCount;

        // checa aridade
        if (args.size() < minArgs || args.size() > maxArgs) {
            addReport(error(callNode,
                    "Chamada a '" + mName + "' com " + args.size() +
                            " args, mas esperava " + (isVar ? (minArgs + "+") : pCount)));
        }

        // checa tipo de cada argumento
        for (int i = 0; i < args.size(); i++) {
            Type expected = (i < pCount)
                    ? formals.get(Math.min(i, pCount - 1)).getType()
                    : formals.get(pCount - 1).getType();
            Type actual = inferType(args.get(i), table);

            if (!actual.equals(expected)) {
                addReport(error(args.get(i),
                        "Argumento " + (i + 1) + " de '" + mName +
                                "' tem tipo " + actual + " mas esperava " + expected));
            }
        }

        return null;
    }

    private Report error(JmmNode node, String msg) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(), node.getColumn(),
                msg, null
        );
    }

    /** Simples inferência de tipo para suportar este visitor */
    private Type inferType(JmmNode expr, SymbolTable table) {
        switch (expr.getKind()) {
            case "IntegerLiteral":
                return new Type("int", false);
            case "BooleanTrue":
            case "BooleanFalse":
                return new Type("boolean", false);
            case "VarRefExpr": {
                String name = expr.get("value");
                // procura em locais
                for (Symbol s : table.getLocalVariables(currentMethod))
                    if (s.getName().equals(name)) return s.getType();
                // procura em params
                for (Symbol s : table.getParameters(currentMethod))
                    if (s.getName().equals(name)) return s.getType();
                // procura em fields
                for (Symbol s : table.getFields())
                    if (s.getName().equals(name)) return s.getType();
                return new Type("unknown", false);
            }
            case "MethodCallExpr":
                return table.getReturnType(expr.get("method"));
            default:
                return new Type("unknown", false);
        }
    }
}
