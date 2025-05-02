package pt.up.fe.comp2025.analysis.passes;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class DuplicateDeclarationCheck extends AnalysisVisitor {

    // campos a nível de classe
    private final Set<String> seenFields = new HashSet<>();
    // para cada método, reinicializamos estes dois
    private Set<String> seenParams;
    private Set<String> seenLocals;

    @Override
    protected void buildVisitor() {
        // ao entrar num método, resetamos parâmetros e locais
        addVisit(Kind.METHOD_DECL, this::visitMethod);
        // visitamos qualquer declaração de variável (VAR_DECL)
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        // e qualquer parâmetro (PARAM)
        addVisit(Kind.PARAM, this::visitParam);
    }

    private Void visitMethod(JmmNode methodDecl, SymbolTable table) {
        seenParams = new HashSet<>();
        seenLocals = new HashSet<>();
        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        String name = varDecl.get("name");
        if (seenParams == null) {
            // ainda não entrámos num método → campo de classe
            if (!seenFields.add(name)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        varDecl.getLine(),
                        varDecl.getColumn(),
                        "Campo duplicado: '" + name + "'",
                        null));
            }
        } else {
            // dentro de método → variável local
            if (!seenLocals.add(name)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        varDecl.getLine(),
                        varDecl.getColumn(),
                        "Variável local duplicada: '" + name + "'",
                        null));
            }
        }
        return null;
    }

    private Void visitParam(JmmNode paramDecl, SymbolTable table) {
        String name = paramDecl.get("name");
        if (!seenParams.add(name)) {
            // encontra o método ancestral em que este parâmetro aparece
            Optional<JmmNode> optMethod = paramDecl.getAncestor(Kind.METHOD_DECL);
            String methodName = optMethod
                    .map(m -> m.get("name"))
                    .orElse("<unknown>");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    paramDecl.getLine(),
                    paramDecl.getColumn(),
                    "Parâmetro duplicado em '" + methodName + "': '" + name + "'",
                    null));
        }
        return null;
    }
}
