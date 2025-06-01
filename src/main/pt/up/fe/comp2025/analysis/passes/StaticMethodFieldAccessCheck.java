package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.List;

/**
 * Checks that static methods do not access instance fields or use the "this" keyword.
 * In Java, static methods cannot access instance members since they belong to the class
 * rather than to any specific instance.
 */
public class StaticMethodFieldAccessCheck extends AnalysisVisitor {

    private String currentMethod;
    private boolean isCurrentMethodStatic;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.THIS_EXPR, this::visitThisExpr);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        
        // In this codebase, static methods are identified as "main" method
        // Based on the pattern seen throughout the codebase
        isCurrentMethodStatic = "main".equals(currentMethod);
        
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        if (!isCurrentMethodStatic || currentMethod == null) {
            return null;
        }

        String varName = varRefExpr.get("value");
        
        // Check if this variable reference is actually a field access
        if (isFieldAccess(varName, table)) {
            addReport(Report.newError(
                Stage.SEMANTIC,
                varRefExpr.getLine(),
                varRefExpr.getColumn(),
                String.format("Static method '%s' cannot access instance field '%s'", 
                    currentMethod, varName),
                null
            ));
        }
        
        return null;
    }

    private Void visitThisExpr(JmmNode thisExpr, SymbolTable table) {
        if (!isCurrentMethodStatic || currentMethod == null) {
            return null;
        }

        addReport(Report.newError(
            Stage.SEMANTIC,
            thisExpr.getLine(),
            thisExpr.getColumn(),
            String.format("Static method '%s' cannot use 'this' keyword", currentMethod),
            null
        ));
        
        return null;
    }

    /**
     * Determines if a variable reference is actually accessing an instance field.
     * A variable is considered a field access if:
     * 1. It's not a local variable in the current method
     * 2. It's not a parameter of the current method  
     * 3. It's not an imported class name
     * 4. It is declared as a class field
     */
    private boolean isFieldAccess(String varName, SymbolTable table) {
        // Check if it's a local variable
        List<Symbol> locals = table.getLocalVariables(currentMethod);
        if (locals != null && locals.stream()
                .anyMatch(local -> local.getName().equals(varName))) {
            return false;
        }

        // Check if it's a method parameter
        List<Symbol> parameters = table.getParameters(currentMethod);
        if (parameters != null && parameters.stream()
                .anyMatch(param -> param.getName().equals(varName))) {
            return false;
        }

        // Check if it matches an imported class
        List<String> imports = table.getImports();
        if (imports != null && imports.stream().anyMatch(importStr -> {
            int lastDot = importStr.lastIndexOf('.');
            String simpleName = (lastDot >= 0) ? importStr.substring(lastDot + 1) : importStr;
            return simpleName.equals(varName);
        })) {
            return false;
        }

        // Check if it's a class field
        List<Symbol> fields = table.getFields();
        return fields != null && fields.stream()
                .anyMatch(field -> field.getName().equals(varName));
    }
}
