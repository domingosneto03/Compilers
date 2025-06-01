// RegisterAllocation.java
package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;
import java.util.stream.Collectors;

public class RegisterAllocation {

    private final OllirResult ollirResult;
    private final int maxRegisters;
    private final boolean optimize;

    public RegisterAllocation(OllirResult ollirResult, int maxRegisters) {
        this.ollirResult = ollirResult;
        this.maxRegisters = maxRegisters;
        this.optimize = maxRegisters >= 0; // -1 means no optimization
    }

    public OllirResult allocateRegisters() {
        if (!optimize) {
            return ollirResult;
        }

        ClassUnit classUnit = ollirResult.getOllirClass();
        List<Report> reports = new ArrayList<>(ollirResult.getReports());

        classUnit.buildCFGs();

        for (Method method : classUnit.getMethods()) {
            try {
                allocateRegistersForMethod(method);
            } catch (RegisterAllocationException e) {
                reports.add(Report.newError(
                        Stage.OPTIMIZATION,
                        -1, -1,
                        "Register allocation failed for method " + method.getMethodName() +
                                ": " + e.getMessage(),
                        null
                ));
            }
        }

        if (!reports.equals(ollirResult.getReports())) {
            if (reports.size() > ollirResult.getReports().size()) {
                System.err.println("Register allocation errors occurred:");
                for (int i = ollirResult.getReports().size(); i < reports.size(); i++) {
                    System.err.println(reports.get(i));
                }
            }
        }

        return ollirResult;
    }

    private void allocateRegistersForMethod(Method method) throws RegisterAllocationException {
        System.out.println("=== Allocating registers for method: " + method.getMethodName() + " ===");

        Set<String> localVariables = getLocalVariablesToAllocate(method);
        if (localVariables.isEmpty()) {
            System.out.println("No local variables to allocate for method " + method.getMethodName());
            System.out.println("=== End register allocation ===");
            return;
        }

        System.out.println("Original variable table:");
        for (Map.Entry<String, Descriptor> entry : method.getVarTable().entrySet()) {
            System.out.println("  " + entry.getKey() + " -> register " + entry.getValue().getVirtualReg() +
                    " (scope: " + entry.getValue().getScope() + ")");
        }

        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.analyze();

        InterferenceGraph interferenceGraph = new InterferenceGraph(method, liveness);
        interferenceGraph.build();

        System.out.println("Variables for interference graph: " + interferenceGraph.getVariables());

        GraphColoring coloring = new GraphColoring(interferenceGraph, method, maxRegisters);
        Map<String, Integer> allocation = coloring.color();

        updateVarTable(method, allocation);

        System.out.println("Final variable table after allocation:");
        for (Map.Entry<String, Descriptor> entry : method.getVarTable().entrySet()) {
            System.out.println("  " + entry.getKey() + " -> register " + entry.getValue().getVirtualReg());
        }

        String report = RegisterAllocationUtils.generateAllocationReport(method, allocation);
        System.out.println(report);

        System.out.println("Total unique registers: " + RegisterAllocationUtils.countUniqueRegisters(method));
        System.out.println("=== End register allocation ===");
    }

    private Set<String> getLocalVariablesToAllocate(Method method) {
        Set<String> variables = new HashSet<>();
        Map<String, Descriptor> varTable = method.getVarTable();

        for (String varName : varTable.keySet()) {
            Descriptor desc = varTable.get(varName);
            if (desc.getScope() == VarScope.LOCAL && !"this".equals(varName)) {
                variables.add(varName);
            }
        }

        return variables;
    }

    private void updateVarTable(Method method, Map<String, Integer> allocation) {
        Map<String, Descriptor> varTable = method.getVarTable();

        for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
            String varName = entry.getKey();
            Integer register = entry.getValue();

            if (varTable.containsKey(varName)) {
                Descriptor descriptor = varTable.get(varName);
                if (descriptor.getScope() == VarScope.LOCAL && !"this".equals(varName)) {
                    System.out.println("Updating " + varName + " from register " +
                            descriptor.getVirtualReg() + " to register " + register);
                    descriptor.setVirtualReg(register);
                }
            }
        }
    }

    private static class RegisterAllocationException extends Exception {
        public RegisterAllocationException(String message) {
            super(message);
        }
    }

    private static class LivenessAnalysis {
        private final Method method;
        private final Map<Instruction, Set<String>> defSets;
        private final Map<Instruction, Set<String>> useSets;
        private final Map<Instruction, Set<String>> liveIns;
        private final Map<Instruction, Set<String>> liveOuts;
        private boolean analyzed = false;

        public LivenessAnalysis(Method method) {
            this.method = method;
            this.defSets = new HashMap<>();
            this.useSets = new HashMap<>();
            this.liveIns = new HashMap<>();
            this.liveOuts = new HashMap<>();
        }

        public LivenessAnalysis analyze() {
            if (analyzed) return this;

            for (Instruction inst : method.getInstructions()) {
                defSets.put(inst, new HashSet<>());
                useSets.put(inst, new HashSet<>());
                liveIns.put(inst, new HashSet<>());
                liveOuts.put(inst, new HashSet<>());
            }

            calculateDefUseSets();

            calculateLiveInOut();

            if (System.getProperty("debug.regalloc") != null) {
                RegisterAllocationUtils.printLivenessAnalysis(method, liveIns, liveOuts, defSets, useSets);
            }

            analyzed = true;
            return this;
        }

        private void calculateDefUseSets() {
            for (Instruction inst : method.getInstructions()) {
                Set<String> defs = defSets.get(inst);
                Set<String> uses = useSets.get(inst);

                switch (inst.getInstType()) {
                    case ASSIGN:
                        AssignInstruction assign = (AssignInstruction) inst;
                        Element dest = assign.getDest();
                        if (dest instanceof Operand) {
                            String varName = ((Operand) dest).getName();
                            if (!"this".equals(varName)) {
                                defs.add(varName);
                            }
                        }
                        addOperandsToUseSet(assign.getRhs(), uses);
                        break;

                    case CALL:
                        CallInstruction call = (CallInstruction) inst;
                        if (call.getArguments() != null) {
                            for (Element arg : call.getArguments()) {
                                if (arg instanceof Operand) {
                                    String varName = ((Operand) arg).getName();
                                    if (!"this".equals(varName)) {
                                        uses.add(varName);
                                    }
                                }
                            }
                        }
                        if (call.getCaller() instanceof Operand) {
                            String varName = ((Operand) call.getCaller()).getName();
                            if (!"this".equals(varName)) {
                                uses.add(varName);
                            }
                        }
                        break;

                    case RETURN:
                        ReturnInstruction ret = (ReturnInstruction) inst;
                        if (ret.getOperand().isPresent() && ret.getOperand().get() instanceof Operand) {
                            String varName = ((Operand) ret.getOperand().get()).getName();
                            if (!"this".equals(varName)) {
                                uses.add(varName);
                            }
                        }
                        break;

                    case BRANCH:
                        if (inst instanceof CondBranchInstruction) {
                            CondBranchInstruction branch = (CondBranchInstruction) inst;
                            for (Element operand : branch.getOperands()) {
                                if (operand instanceof Operand) {
                                    String varName = ((Operand) operand).getName();
                                    if (!"this".equals(varName)) {
                                        uses.add(varName);
                                    }
                                }
                            }
                        }
                        break;

                    default:
                        break;
                }
            }
        }

        private void addOperandsToUseSet(Instruction inst, Set<String> uses) {
            switch (inst.getInstType()) {
                case BINARYOPER:
                    BinaryOpInstruction binOp = (BinaryOpInstruction) inst;
                    if (binOp.getLeftOperand() instanceof Operand) {
                        String varName = ((Operand) binOp.getLeftOperand()).getName();
                        if (!"this".equals(varName)) {
                            uses.add(varName);
                        }
                    }
                    if (binOp.getRightOperand() instanceof Operand) {
                        String varName = ((Operand) binOp.getRightOperand()).getName();
                        if (!"this".equals(varName)) {
                            uses.add(varName);
                        }
                    }
                    break;

                case UNARYOPER:
                    UnaryOpInstruction unOp = (UnaryOpInstruction) inst;
                    if (unOp.getOperand() instanceof Operand) {
                        String varName = ((Operand) unOp.getOperand()).getName();
                        if (!"this".equals(varName)) {
                            uses.add(varName);
                        }
                    }
                    break;

                case NOPER:
                    SingleOpInstruction singleOp = (SingleOpInstruction) inst;
                    if (singleOp.getSingleOperand() instanceof Operand) {
                        String varName = ((Operand) singleOp.getSingleOperand()).getName();
                        if (!"this".equals(varName)) {
                            uses.add(varName);
                        }
                    }
                    break;

                case CALL:
                    CallInstruction call = (CallInstruction) inst;
                    if (call.getArguments() != null) {
                        for (Element arg : call.getArguments()) {
                            if (arg instanceof Operand) {
                                String varName = ((Operand) arg).getName();
                                if (!"this".equals(varName)) {
                                    uses.add(varName);
                                }
                            }
                        }
                    }
                    if (call.getCaller() instanceof Operand) {
                        String varName = ((Operand) call.getCaller()).getName();
                        if (!"this".equals(varName)) {
                            uses.add(varName);
                        }
                    }
                    break;

                default:
                    break;
            }
        }

        private void calculateLiveInOut() {
            boolean changed = true;

            while (changed) {
                changed = false;

                for (Instruction inst : method.getInstructions()) {
                    Set<String> oldLiveIn = new HashSet<>(liveIns.get(inst));
                    Set<String> oldLiveOut = new HashSet<>(liveOuts.get(inst));

                    Set<String> newLiveOut = new HashSet<>();
                    for (Node successor : inst.getSuccessors()) {
                        if (successor instanceof Instruction) {
                            newLiveOut.addAll(liveIns.get((Instruction) successor));
                        }
                    }

                    Set<String> newLiveIn = new HashSet<>(useSets.get(inst));
                    Set<String> liveOutMinusDef = new HashSet<>(newLiveOut);
                    liveOutMinusDef.removeAll(defSets.get(inst));
                    newLiveIn.addAll(liveOutMinusDef);

                    liveIns.put(inst, newLiveIn);
                    liveOuts.put(inst, newLiveOut);

                    if (!oldLiveIn.equals(newLiveIn) || !oldLiveOut.equals(newLiveOut)) {
                        changed = true;
                    }
                }
            }
        }

        public Set<String> getLiveIn(Instruction inst) {
            return liveIns.get(inst);
        }

        public Set<String> getLiveOut(Instruction inst) {
            return liveOuts.get(inst);
        }

        public Set<String> getDef(Instruction inst) {
            return defSets.get(inst);
        }
    }

    private static class InterferenceGraph {
        private final Method method;
        private final LivenessAnalysis liveness;
        private final Map<String, Set<String>> adjacencyList;
        private final Set<String> variables;

        public InterferenceGraph(Method method, LivenessAnalysis liveness) {
            this.method = method;
            this.liveness = liveness;
            this.adjacencyList = new HashMap<>();
            this.variables = new HashSet<>();
        }

        public InterferenceGraph build() {
            collectVariables();

            for (String var : variables) {
                adjacencyList.put(var, new HashSet<>());
            }

            for (Instruction inst : method.getInstructions()) {
                Set<String> interferingVars = new HashSet<>(liveness.getDef(inst));
                interferingVars.addAll(liveness.getLiveOut(inst));

                List<String> varList = new ArrayList<>(interferingVars);
                for (int i = 0; i < varList.size(); i++) {
                    for (int j = i + 1; j < varList.size(); j++) {
                        String var1 = varList.get(i);
                        String var2 = varList.get(j);

                        if (variables.contains(var1) && variables.contains(var2)) {
                            adjacencyList.get(var1).add(var2);
                            adjacencyList.get(var2).add(var1);
                        }
                    }
                }
            }

            if (System.getProperty("debug.regalloc") != null) {
                RegisterAllocationUtils.printInterferenceGraph(adjacencyList);
            }

            return this;
        }

        private void collectVariables() {
            Map<String, Descriptor> varTable = method.getVarTable();

            for (String varName : varTable.keySet()) {
                Descriptor desc = varTable.get(varName);
                if (desc.getScope() == VarScope.LOCAL && !"this".equals(varName)) {
                    variables.add(varName);
                }
            }

        }

        public Set<String> getVariables() {
            return new HashSet<>(variables);
        }

        public Set<String> getNeighbors(String variable) {
            return adjacencyList.getOrDefault(variable, new HashSet<>());
        }

        public int getDegree(String variable) {
            return adjacencyList.getOrDefault(variable, new HashSet<>()).size();
        }

        public void removeVariable(String variable) {
            Set<String> neighbors = adjacencyList.remove(variable);
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    adjacencyList.get(neighbor).remove(variable);
                }
            }
            variables.remove(variable);
        }
    }

    // Graph Coloring Implementation
    private static class GraphColoring {
        private final InterferenceGraph graph;
        private final Method method;
        private final int maxColors;
        private final Stack<String> removalStack;

        public GraphColoring(InterferenceGraph graph, Method method, int maxColors) {
            this.graph = graph;
            this.method = method;
            this.maxColors = maxColors == 0 ? Integer.MAX_VALUE : maxColors;
            this.removalStack = new Stack<>();
        }

        public Map<String, Integer> color() throws RegisterAllocationException {
            if (graph.getVariables().isEmpty()) {
                return new HashMap<>();
            }

            // Store original adjacency list
            Map<String, Set<String>> originalAdjacencyList = new HashMap<>();
            for (String var : graph.getVariables()) {
                originalAdjacencyList.put(var, new HashSet<>(graph.getNeighbors(var)));
            }

            while (!graph.getVariables().isEmpty()) {
                String nodeToRemove = findRemovableNode();
                if (nodeToRemove == null) {
                    throw new RegisterAllocationException(
                            "Cannot allocate with " + maxColors + " registers. Need at least " +
                                    calculateMinimumRegisters() + " registers."
                    );
                }
                removalStack.push(nodeToRemove);
                graph.removeVariable(nodeToRemove);
            }

            Map<String, Integer> allocation = new HashMap<>();

            int nextAvailableRegister = reserveParameterRegisters();

            while (!removalStack.isEmpty()) {
                String variable = removalStack.pop();
                int color = assignColor(variable, allocation, nextAvailableRegister, originalAdjacencyList);
                allocation.put(variable, color);
            }

            return allocation;
        }

        private String findRemovableNode() {
            for (String variable : graph.getVariables()) {
                if (graph.getDegree(variable) < maxColors) {
                    return variable;
                }
            }
            return null;
        }

        private int calculateMinimumRegisters() {
            return graph.getVariables().stream()
                    .mapToInt(graph::getDegree)
                    .max()
                    .orElse(0) + 1;
        }

        private int reserveParameterRegisters() {
            int nextRegister = 0;
            Map<String, Descriptor> varTable = method.getVarTable();

            Set<Integer> reservedRegisters = new HashSet<>();
            for (Map.Entry<String, Descriptor> entry : varTable.entrySet()) {
                Descriptor desc = entry.getValue();
                if (desc.getScope() == VarScope.PARAMETER || "this".equals(entry.getKey())) {
                    reservedRegisters.add(desc.getVirtualReg());
                }
            }

            while (reservedRegisters.contains(nextRegister)) {
                nextRegister++;
            }

            System.out.println("Reserved registers: " + reservedRegisters);
            System.out.println("Starting register allocation from register: " + nextRegister);
            return nextRegister;
        }

        private int assignColor(String variable, Map<String, Integer> allocation,
                                int startRegister, Map<String, Set<String>> originalAdjacencyList) {
            Set<String> neighbors = originalAdjacencyList.getOrDefault(variable, Collections.emptySet());
            Set<Integer> usedColors = neighbors.stream()
                    .filter(allocation::containsKey)
                    .map(allocation::get)
                    .collect(Collectors.toSet());

            Map<String, Descriptor> varTable = method.getVarTable();
            for (Map.Entry<String, Descriptor> entry : varTable.entrySet()) {
                Descriptor desc = entry.getValue();
                if (desc.getScope() == VarScope.PARAMETER || "this".equals(entry.getKey())) {
                    usedColors.add(desc.getVirtualReg());
                }
            }

            int color = startRegister;
            while (usedColors.contains(color)) {
                color++;
            }

            return color;
        }
    }
}