package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.optimization.visitors.ConstantFoldingVisitor;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        var rootNode = semanticsResult.getRootNode();

        var constFold = new ConstantFoldingVisitor();
        constFold.visit(rootNode);

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        //TODO: Do your OLLIR-based optimizations here

        return ollirResult;
    }


}
