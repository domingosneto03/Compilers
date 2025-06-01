package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;

import java.util.*;

/**
 * Utility class for register allocation debugging and reporting
 */
public class RegisterAllocationUtils {

    /**
     * Print detailed liveness analysis information for debugging
     */
    public static void printLivenessAnalysis(Method method,
                                             Map<Instruction, Set<String>> liveIns,
                                             Map<Instruction, Set<String>> liveOuts,
                                             Map<Instruction, Set<String>> defSets,
                                             Map<Instruction, Set<String>> useSets) {
        System.out.println("=== Liveness Analysis for " + method.getMethodName() + " ===");

        int instIndex = 0;
        for (Instruction inst : method.getInstructions()) {
            System.out.println("Instruction " + instIndex + ": " + inst);
            System.out.println("  DEF: " + defSets.getOrDefault(inst, Collections.emptySet()));
            System.out.println("  USE: " + useSets.getOrDefault(inst, Collections.emptySet()));
            System.out.println("  IN:  " + liveIns.getOrDefault(inst, Collections.emptySet()));
            System.out.println("  OUT: " + liveOuts.getOrDefault(inst, Collections.emptySet()));
            System.out.println();
            instIndex++;
        }
    }

    /**
     * Print interference graph for debugging
     */
    public static void printInterferenceGraph(Map<String, Set<String>> adjacencyList) {
        System.out.println("=== Interference Graph ===");
        for (Map.Entry<String, Set<String>> entry : adjacencyList.entrySet()) {
            System.out.println(entry.getKey() + " interferes with: " + entry.getValue());
        }
        System.out.println();
    }

    /**
     * Validate register allocation result
     * Fixed: Only validate LOCAL variables that were supposed to be allocated
     */
    public static boolean validateAllocation(Method method, Map<String, Integer> allocation) {
        System.out.println("=== Validating Register Allocation ===");

        Map<String, Descriptor> varTable = method.getVarTable();
        for (String varName : varTable.keySet()) {
            Descriptor desc = varTable.get(varName);
            if (desc.getScope() == VarScope.LOCAL && !"this".equals(varName)) {
                if (!allocation.containsKey(varName)) {
                    System.err.println("ERROR: Local variable " + varName + " not allocated a register");
                    return false;
                }
            }
        }

        // Check for valid register numbers
        for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
            Integer register = entry.getValue();
            if (register < 0) {
                System.err.println("ERROR: Invalid register " + register + " for variable " + entry.getKey());
                return false;
            }
        }

        // Check that registers are properly assigned
        Set<Integer> reservedRegisters = new HashSet<>();
        for (Map.Entry<String, Descriptor> entry : varTable.entrySet()) {
            Descriptor desc = entry.getValue();
            if (desc.getScope() == VarScope.PARAMETER || "this".equals(entry.getKey())) {
                reservedRegisters.add(desc.getVirtualReg());
            }
        }

        for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
            Integer register = entry.getValue();
            if (reservedRegisters.contains(register)) {
                System.err.println("ERROR: Allocated register " + register + " for variable " +
                        entry.getKey() + " conflicts with reserved register");
                return false;
            }
        }

        Set<Integer> allocatedRegisters = new HashSet<>(allocation.values());
        System.out.println("Validation passed. Allocated registers: " + allocatedRegisters);
        System.out.println("Reserved registers: " + reservedRegisters);
        return true;
    }

    /**
     * Get all variable names used in a method (for debugging)
     */
    public static Set<String> getAllVariables(Method method) {
        Set<String> variables = new HashSet<>();

        for (Instruction inst : method.getInstructions()) {
            variables.addAll(getVariablesFromInstruction(inst));
        }

        return variables;
    }

    /**
     * Extract variable names from an instruction
     */
    public static Set<String> getVariablesFromInstruction(Instruction inst) {
        Set<String> variables = new HashSet<>();

        switch (inst.getInstType()) {
            case ASSIGN:
                AssignInstruction assign = (AssignInstruction) inst;
                if (assign.getDest() instanceof Operand) {
                    variables.add(((Operand) assign.getDest()).getName());
                }
                variables.addAll(getVariablesFromInstruction(assign.getRhs()));
                break;

            case BINARYOPER:
                BinaryOpInstruction binOp = (BinaryOpInstruction) inst;
                if (binOp.getLeftOperand() instanceof Operand) {
                    variables.add(((Operand) binOp.getLeftOperand()).getName());
                }
                if (binOp.getRightOperand() instanceof Operand) {
                    variables.add(((Operand) binOp.getRightOperand()).getName());
                }
                break;

            case UNARYOPER:
                UnaryOpInstruction unOp = (UnaryOpInstruction) inst;
                if (unOp.getOperand() instanceof Operand) {
                    variables.add(((Operand) unOp.getOperand()).getName());
                }
                break;

            case NOPER:
                SingleOpInstruction singleOp = (SingleOpInstruction) inst;
                if (singleOp.getSingleOperand() instanceof Operand) {
                    variables.add(((Operand) singleOp.getSingleOperand()).getName());
                }
                break;

            case CALL:
                CallInstruction call = (CallInstruction) inst;
                if (call.getArguments() != null) {
                    for (Element arg : call.getArguments()) {
                        if (arg instanceof Operand) {
                            variables.add(((Operand) arg).getName());
                        }
                    }
                }
                if (call.getCaller() instanceof Operand) {
                    variables.add(((Operand) call.getCaller()).getName());
                }
                break;

            case RETURN:
                ReturnInstruction ret = (ReturnInstruction) inst;
                if (ret.getOperand().isPresent() && ret.getOperand().get() instanceof Operand) {
                    variables.add(((Operand) ret.getOperand().get()).getName());
                }
                break;

            case BRANCH:
                if (inst instanceof CondBranchInstruction) {
                    CondBranchInstruction branch = (CondBranchInstruction) inst;
                    for (Element operand : branch.getOperands()) {
                        if (operand instanceof Operand) {
                            variables.add(((Operand) operand).getName());
                        }
                    }
                }
                break;

            default:
                break;
        }

        return variables;
    }

    /**
     * Count the number of unique registers used in a method after allocation
     */
    public static int countUniqueRegisters(Method method) {
        Set<Integer> registers = new HashSet<>();

        for (Descriptor desc : method.getVarTable().values()) {
            registers.add(desc.getVirtualReg());
        }

        return registers.size();
    }

    /**
     * Generate a report of register allocation results
     */
    public static String generateAllocationReport(Method method, Map<String, Integer> allocation) {
        StringBuilder report = new StringBuilder();
        report.append("Register Allocation Report for ").append(method.getMethodName()).append(":\n");
        report.append("----------------------------------------\n");

        // Sort by register number
        Map<Integer, List<String>> registerToVars = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
            int register = entry.getValue();
            registerToVars.computeIfAbsent(register, k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<Integer, List<String>> entry : registerToVars.entrySet()) {
            report.append("Register ").append(entry.getKey()).append(": ");
            report.append(String.join(", ", entry.getValue())).append("\n");
        }

        report.append("Total registers used: ").append(registerToVars.size()).append("\n");

        return report.toString();
    }

    /**
     * Check if two variables interfere based on liveness information
     */
    public static boolean doVariablesInterfere(String var1, String var2, Method method,
                                               Map<Instruction, Set<String>> liveIns,
                                               Map<Instruction, Set<String>> liveOuts,
                                               Map<Instruction, Set<String>> defSets) {
        for (Instruction inst : method.getInstructions()) {
            Set<String> interferingVars = new HashSet<>(defSets.getOrDefault(inst, Collections.emptySet()));
            interferingVars.addAll(liveOuts.getOrDefault(inst, Collections.emptySet()));

            if (interferingVars.contains(var1) && interferingVars.contains(var2)) {
                return true;
            }
        }
        return false;
    }
}
