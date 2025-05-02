package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class ReturnCheckVisitor extends AnalysisVisitor {

    private String currentMethod;
    private Type declaredReturn;

    @Override
    public void buildVisitor() {
        // 1) Ao entrar num METHOD_DECL, guarda estado
        addVisit(Kind.METHOD_DECL, this::enterMethod);
        // 2) Em cada RETURN_STMT, processa-o imediatamente
        addVisit(Kind.RETURN_STMT, this::checkReturn);
    }

    private Void enterMethod(JmmNode methodDecl, SymbolTable table) {
        currentMethod = methodDecl.get("name");
        declaredReturn = table.getReturnType(currentMethod);
        return null;
    }

    private Void checkReturn(JmmNode returnNode, SymbolTable table) {
        // se for método void
        if (declaredReturn == null || declaredReturn.getName().equals("void")) {
            if (returnNode.getChildren().size() > 0) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        returnNode.getLine(),
                        returnNode.getColumn(),
                        "Método '" + currentMethod + "' não pode devolver valor",
                        null
                ));
            }
            return null;
        }

        // método não-void sem return expr;
        if (returnNode.getChildren().isEmpty()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    returnNode.getLine(),
                    returnNode.getColumn(),
                    "Método '" + currentMethod + "' deve devolver '" + declaredReturn.getName() + "'",
                    null
            ));
            return null;
        }

        // inferir tipo da expressão de return
        JmmNode exprNode = returnNode.getChildren().get(0);
        Type exprType;
        try {
            exprType = TypeUtils.inferExprType(exprNode, table, currentMethod);
        } catch (RuntimeException e) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    returnNode.getLine(),
                    returnNode.getColumn(),
                    "Falha ao avaliar expressão de return: " + e.getMessage(),
                    null
            ));
            return null;
        }

        // verificar compatibilidade
        if (!TypeUtils.compatibleWith(declaredReturn, exprType, table)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    returnNode.getLine(),
                    returnNode.getColumn(),
                    "Tipo de retorno incompatível: esperava '" + declaredReturn.getName()
                            + "', mas a expressão é '" + exprType.getName() + "'",
                    null
            ));
        }

        return null;
    }
}
