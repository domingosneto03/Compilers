package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.Collections;
import java.util.List;

/**
 * Checks if an identifier used in the code is declared as either a local variable,
 * a method parameter, a class field, or corresponds to an imported class.
 * If not, an error report is generated.
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        // Record current method from METHOD_DECL nodes.
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        // Visit variable reference expressions.
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        // Set the current method context using the "name" attribute.
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        //System.out.println("[DEBUG] UndeclaredVariable — entering method: " + currentMethod);
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Get the identifier from the "value" attribute (per your grammar).
        String varRefName = varRefExpr.get("value");

        // Check if it is declared as a method parameter.
        List<pt.up.fe.comp.jmm.analysis.table.Symbol> parameters = table.getParameters(currentMethod);
        if (parameters != null && parameters.stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Check if it is declared as a local variable.
        List<pt.up.fe.comp.jmm.analysis.table.Symbol> localVariables = table.getLocalVariables(currentMethod);
        if (localVariables != null && localVariables.stream()
                .anyMatch(local -> local.getName().equals(varRefName))) {
            return null;
        }

        // Check if it is declared as a class field.
        List<pt.up.fe.comp.jmm.analysis.table.Symbol> fields = table.getFields();
        if (fields != null && fields.stream()
                .anyMatch(field -> field.getName().equals(varRefName))) {
            return null;
        }

        // Check if it matches an imported class.
        List<String> imports = table.getImports();
        if (imports != null && imports.stream().anyMatch(importStr -> {
            int lastDot = importStr.lastIndexOf('.');
            String simpleName = (lastDot >= 0) ? importStr.substring(lastDot + 1) : importStr;
            return simpleName.equals(varRefName);
        })) {
            return null;
        }

        if (parameters == null) {
            String message = String.format("Method '%s' not found in symbol table when checking variable '%s'.",
                    currentMethod, varRefName);
            addReport(Report.newError(Stage.SEMANTIC,
                    varRefExpr.getLine(),
                    varRefExpr.getColumn(),
                    message,
                    null));
            return null;
        }

        String message = String.format("Variable '%s' does not exist.", varRefName);
        addReport(Report.newError(Stage.SEMANTIC,
                varRefExpr.getLine(),
                varRefExpr.getColumn(),
                message,
                null));

        return null;
    }
}