package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.List;

public class VarargsCheck extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::checkMethodDecl);
        addVisit(Kind.VAR_DECL, this::checkVarDecl);
    }

    private Void checkMethodDecl(JmmNode methodDecl, SymbolTable table) {
        List<JmmNode> params = methodDecl.getChildren(Kind.PARAM);
        int varargCount = 0;

        for (int i = 0; i < params.size(); i++) {
            JmmNode param = params.get(i);
            if (param.getKind().equals(Kind.VAR_ARGS.getNodeName())) {
                varargCount++;
                if (i != params.size() - 1) {
                    addReport(Report.newError(Stage.SEMANTIC,
                            param.getLine(), param.getColumn(),
                            "Varargs must be the last parameter in a method.", null));
                }
            }
        }

        if (varargCount > 1) {
            addReport(Report.newError(Stage.SEMANTIC,
                    methodDecl.getLine(), methodDecl.getColumn(),
                    "Only one varargs parameter is allowed per method.", null));
        }

        List<JmmNode> typeNodes = methodDecl.getChildren(Kind.VAR_ARGS);
        if (!typeNodes.isEmpty()) {
            addReport(Report.newError(Stage.SEMANTIC,
                    typeNodes.get(0).getLine(), typeNodes.get(0).getColumn(),
                    "Methods cannot return a varargs type.", null));
        }

        return null;
    }

    private Void checkVarDecl(JmmNode varDecl, SymbolTable table) {
        if (varDecl.getKind().equals(Kind.VAR_ARGS.getNodeName())) {
            addReport(Report.newError(Stage.SEMANTIC,
                    varDecl.getLine(), varDecl.getColumn(),
                    "Varargs cannot be used in variable or field declarations.", null));
        }

        return null;
    }
}
