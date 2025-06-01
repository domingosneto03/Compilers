package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.ConfigOptions;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        var config = semanticsResult.getConfig();

        boolean optimizeEnabled = ConfigOptions.getOptimize(config);

        System.out.println("Optimization flag (-o) enabled: " + optimizeEnabled);

        if (!optimizeEnabled) {
            return semanticsResult;
        }

        var root = semanticsResult.getRootNode();
        var table = semanticsResult.getSymbolTable();

        boolean globalChanged;
        int iterations = 0;

        do {
            globalChanged = false;
            iterations++;
            
            System.out.println("--- Optimization iteration " + iterations + " ---");

            ConstantPropagationVisitor propagation = new ConstantPropagationVisitor(table);
            propagation.visit(root);
            boolean propagationChanged = propagation.didChange();
            globalChanged |= propagationChanged;
            System.out.println("Constant propagation changed: " + propagationChanged);

            ConstantFoldingVisitor folder = new ConstantFoldingVisitor(table);
            folder.visit(root);
            boolean foldingChanged = folder.didChange();
            globalChanged |= foldingChanged;
            System.out.println("Constant folding changed: " + foldingChanged);

            System.out.println("Total changed in iteration " + iterations + ": " + globalChanged);

        } while (globalChanged);

        System.out.println("Optimization completed after " + iterations + " iterations");
        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        var config = ollirResult.getConfig();

        // Check if register allocation is enabled
        int registerAllocation = ConfigOptions.getRegisterAllocation(config);

        System.out.println("Register allocation (-r) setting: " + registerAllocation);

        if (registerAllocation >= 0) {
            // Apply register allocation
            RegisterAllocation regAlloc = new RegisterAllocation(ollirResult, registerAllocation);
            return regAlloc.allocateRegisters();
        }

        return ollirResult;
    }


}
