package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.ast.Kind;

public class ConditionCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.IF_ELSE_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        //System.out.println("[DEBUG] ConditionCheck — entering method: " + currentMethod);
        return null;
    }

    private Void visitIfStmt(JmmNode node, SymbolTable table) {
        JmmNode condition = node.getChild(0);

        try {
            Type condType = new TypeUtils(table).getExprType(condition, currentMethod);
            
            // Strict boolean check: must be exactly boolean and not an array
            if (condType == null || !"boolean".equals(condType.getName()) || condType.isArray()) {
                String actualType = condType != null ? 
                    (condType.isArray() ? condType.getName() + "[]" : condType.getName()) : 
                    "unknown";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        condition.getLine(),
                        condition.getColumn(),
                        "Condition in 'if' must be boolean, but found: " + actualType,
                        null
                ));
            }
        } catch (RuntimeException e) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    condition.getLine(),
                    condition.getColumn(),
                    "Failed to evaluate 'if' condition: " + e.getMessage(),
                    null
            ));
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode node, SymbolTable table) {
        JmmNode condition = node.getChild(0);

        try {
            Type condType = new TypeUtils(table).getExprType(condition, currentMethod);
            
            // Strict boolean check: must be exactly boolean and not an array
            if (condType == null || !"boolean".equals(condType.getName()) || condType.isArray()) {
                String actualType = condType != null ? 
                    (condType.isArray() ? condType.getName() + "[]" : condType.getName()) : 
                    "unknown";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        condition.getLine(),
                        condition.getColumn(),
                        "Condition in 'while' must be boolean, but found: " + actualType,
                        null
                ));
            }
        } catch (RuntimeException e) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    condition.getLine(),
                    condition.getColumn(),
                    "Failed to evaluate 'while' condition: " + e.getMessage(),
                    null
            ));
        }

        return null;
    }
}
