package pt.up.fe.comp2025.analysis.passes;

import java.util.List;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

public class VarargsDeclarationCheck extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        // register on every MethodDecl node
        addVisit("MethodDecl", this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        // get the method name
        String methodName = methodDecl.get("name");

        // 1) only if this method is in the symbol‐table
        if (!table.getMethods().contains(methodName)) {
            return null;
        }

        // 2) get its parameters (could be null)
        List<Symbol> params = table.getParameters(methodName);
        if (params == null) {
            return null;
        }

        // 3) now safely scan for varargs
        boolean seenVarargs = false;
        for (int i = 0; i < params.size(); i++) {
            Symbol p = params.get(i);

            if (p.getType().isArray()) {
                if (seenVarargs) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            methodDecl.getLine(), methodDecl.getColumn(),
                            "Só pode haver um varargs e ele já apareceu: '" + p.getName() + "'",
                            null));
                } else if (i != params.size() - 1) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            methodDecl.getLine(), methodDecl.getColumn(),
                            "Varargs '" + p.getName() + "' tem de ser o último parâmetro",
                            null));
                }
                seenVarargs = true;
            }
        }

        return null;
    }
}
