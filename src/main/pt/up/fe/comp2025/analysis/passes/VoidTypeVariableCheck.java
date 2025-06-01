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
 * Semantic analysis pass that checks for variables declared with void type.
 * Variables cannot have void type - only methods can have void return type.
 */
public class VoidTypeVariableCheck extends AnalysisVisitor {
    
    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }
    
    /**
     * Visits variable declarations and checks if they have void type.
     * Reports an error if a variable is declared with void type.
     */
    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        // Get the type node (first child of VAR_DECL)
        if (varDecl.getNumChildren() < 1) {
            return null; // Malformed node, should be caught by other checks
        }
        
        JmmNode typeNode = varDecl.getChild(0);
        String variableName = varDecl.get("name");
        
        try {
            // Extract the type using TypeUtils
            Type variableType = TypeUtils.convertType(typeNode);
            
            // Check if the type is void
            if ("void".equals(variableType.getName())) {
                addReport(Report.newError(
                    Stage.SEMANTIC,
                    varDecl.getLine(),
                    varDecl.getColumn(),
                    String.format("Variable '%s' cannot have void type. Only methods can have void return type.", 
                            variableName),
                    null
                ));
            }
        } catch (Exception e) {
            // If type extraction fails, let other analysis passes handle it
            return null;
        }
        
        return null;
    }
}
