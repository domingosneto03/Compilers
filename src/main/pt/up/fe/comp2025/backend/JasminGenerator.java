package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final JasminUtils types;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        types = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(InvokeVirtualInstruction.class, this::generateInvokeVirtual);
        generators.put(InvokeSpecialInstruction.class, this::generateInvokeSpecial);
        generators.put(NewInstruction.class, this::generateNew);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(CondBranchInstruction.class, this::generateCondBranch);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(InvokeStaticInstruction.class, this::generateInvokeStatic);

    }

    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment for debugging
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }

    private String generateCondBranch(CondBranchInstruction condBranch) {
        var code = new StringBuilder();

        // Get the label to branch to
        String label = condBranch.getLabel();

        // Get the condition instruction
        Instruction condition = condBranch.getCondition();

        // Handle different condition types
        if (condition instanceof BinaryOpInstruction) {
            BinaryOpInstruction binOp = (BinaryOpInstruction) condition;

            // Load operands for comparison
            code.append(apply(binOp.getLeftOperand()));
            code.append(apply(binOp.getRightOperand()));

            // Determine the operation type and generate appropriate branch instruction
            var operation = binOp.getOperation().getOpType();
            String branchInst = switch (operation) {
                case LTH -> "if_icmplt";
                case GTH -> "if_icmpgt";
                case LTE -> "if_icmple";
                case GTE -> "if_icmpge";
                case EQ -> "if_icmpeq";
                case NEQ -> "if_icmpne";
                default -> "if_icmplt"; // fallback
            };
            code.append(branchInst).append(" ").append(label).append(NL);
        } else if (condition instanceof SingleOpInstruction) {
            SingleOpInstruction singleOp = (SingleOpInstruction) condition;

            // Load single operand
            code.append(apply(singleOp.getSingleOperand()));

            // For single operand, assume it's a boolean check
            code.append("ifne ").append(label).append(NL);
        } else {
            // Fallback: load all operands and use simple branch
            for (Element operand : condBranch.getOperands()) {
                code.append(apply((TreeNode) operand));
            }

            if (condBranch.getOperands().size() == 1) {
                code.append("ifne ").append(label).append(NL);
            } else {
                code.append("if_icmpne ").append(label).append(NL);
            }
        }

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + NL;
    }

    private String generateInvokeStatic(InvokeStaticInstruction invoke) {
        var code = new StringBuilder();

        // Load all method arguments
        if (!invoke.getArguments().isEmpty()) {
            for (Element arg : invoke.getArguments()) {
                code.append(apply((TreeNode) arg));
            }
        }

        // Get method name
        var methodName = ((LiteralElement) invoke.getMethodName()).getLiteral().replace("\"", "");

        // Get class name from the caller (should be a class reference)
        String className;
        if (invoke.getCaller() instanceof Operand) {
            className = ((Operand) invoke.getCaller()).getName();
        } else {
            className = "UnknownClass";
        }

        // Build method descriptor
        var descriptor = new StringBuilder("(");
        if (!invoke.getArguments().isEmpty()) {
            for (Element arg : invoke.getArguments()) {
                descriptor.append(types.getJasminType(arg.getType()));
            }
        }
        descriptor.append(")");
        descriptor.append(types.getJasminType(invoke.getReturnType()));

        code.append("invokestatic ")
                .append(className).append("/")
                .append(methodName)
                .append(descriptor)
                .append(NL);

        return code.toString();
    }


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class public ").append(className).append(NL);

        // Handle superclass
        var superClass = classUnit.getSuperClass();
        if (superClass != null && !superClass.isEmpty()) {
            code.append(".super ").append(superClass).append(NL);
        } else {
            code.append(".super java/lang/Object").append(NL);
        }
        code.append(NL);

        // Generate fields if any
        for (var field : classUnit.getFields()) {
            var fieldType = types.getJasminType(field.getFieldType());
            code.append(".field public ").append(field.getFieldName()).append(" ").append(fieldType).append(NL);
        }
        if (!classUnit.getFields().isEmpty()) {
            code.append(NL);
        }

        // generate a single constructor method
        var superClassName = (superClass != null && !superClass.isEmpty()) ? superClass : "java/lang/Object";
        var defaultConstructor = """
                .method public <init>()V
                    .limit stack 1
                    .limit locals 1
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                                
                """.formatted(superClassName);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {
        currentMethod = method;

        var code = new StringBuilder();

        // Access modifier (public, private, etc.)
        var modifier = types.getModifier(method.getMethodAccessModifier());

        // Static modifier
        if (method.isStaticMethod()) {
            modifier += "static ";
        }

        var methodName = method.getMethodName();

        // Parameters and return type descriptor
        var params = method.getParams().stream()
                .map(param -> types.getJasminType(param.getType()))
                .collect(Collectors.joining());

        var returnType = types.getJasminType(method.getReturnType());

        // Generate all instructions first to calculate limits properly
        var methodCode = new StringBuilder();
        for (var inst : method.getInstructions()) {
            // Handle labels
            for (String label : method.getLabels(inst)) {
                methodCode.append(label).append(":").append(NL);
            }

            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            methodCode.append(instCode);
        }

        // Calculate limits AFTER generating code
        int stackLimit = calculateStackLimit(method);
        int localsLimit = calculateLocalsLimit(method);

        // Emit method header
        code.append(".method ").append(modifier)
                .append(methodName)
                .append("(").append(params).append(")").append(returnType).append(NL);

        code.append(TAB).append(".limit stack ").append(stackLimit).append(NL);
        code.append(TAB).append(".limit locals ").append(localsLimit).append(NL);

        // Add method instructions
        code.append(methodCode);

        // End method
        code.append(".end method\n");

        currentMethod = null;

        return code.toString();
    }

    private int calculateStackLimit(Method method) {
        int maxStack = 1; // Start with minimum
        
        for (Instruction instr : method.getInstructions()) {
            int stackUsage = calculateInstructionStackUsage(instr);
            maxStack = Math.max(maxStack, stackUsage);
        }
        
        // Add some safety margin but keep reasonable
        return Math.min(maxStack + 2, 10);
    }

    /**
     * Calculate the maximum stack usage for an instruction
     */
    private int calculateInstructionStackUsage(Instruction instr) {
        switch (instr.getInstType()) {
            case ASSIGN:
                AssignInstruction assign = (AssignInstruction) instr;
                if (assign.getDest() instanceof ArrayOperand) {
                    ArrayOperand arrayDest = (ArrayOperand) assign.getDest();
                    // Array assignment: array_ref + indices + value
                    return 2 + arrayDest.getIndexOperands().size();
                }
                // Regular assignment: just the RHS stack usage
                return calculateInstructionStackUsage(assign.getRhs());
                
            case CALL:
                CallInstruction call = (CallInstruction) instr;
                if (call instanceof InvokeVirtualInstruction || call instanceof InvokeSpecialInstruction) {
                    // Instance method: object reference + arguments
                    int argCount = call.getArguments().size();
                    return 1 + argCount; // object ref + args
                } else if (call instanceof InvokeStaticInstruction) {
                    // Static method: just arguments
                    return call.getArguments().size();
                } else if (call instanceof NewInstruction) {
                    NewInstruction newInst = (NewInstruction) call;
                    if (newInst.getReturnType().toString().endsWith("[]")) {
                        // Array creation: size argument
                        return 2; // size + newarray
                    } else {
                        // Object creation: "new" + "dup" = 2 stack slots
                        return 2;
                    }
                } else if (call instanceof ArrayLengthInstruction) {
                    return 1; // array reference
                }
                return 2; // Default conservative
                
            case BINARYOPER:
                BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                // Binary operations need both operands on stack
                if (binOp.getOperation().getOpType().toString().contains("LT") ||
                    binOp.getOperation().getOpType().toString().contains("GT") ||
                    binOp.getOperation().getOpType().toString().contains("EQ")) {
                    return 2; // Comparison operations need 2 values
                }
                return 2; // Most binary ops need 2 operands
                
            case UNARYOPER:
                return 1; // Unary operations need 1 operand
                
            case GETFIELD:
                return 1; // object reference
                
            case PUTFIELD:
                return 2; // object reference + value
                
            case RETURN:
                ReturnInstruction ret = (ReturnInstruction) instr;
                return ret.hasReturnValue() ? 1 : 0;
                
            case BRANCH:
                if (instr instanceof CondBranchInstruction) {
                    CondBranchInstruction condBranch = (CondBranchInstruction) instr;
                    return condBranch.getOperands().size();
                }
                return 0;
                
            case GOTO:
                return 0; // goto doesn't use stack
                
            case NOPER:
                return 1; // single operand
            default:
                return 2; // conservative default
        }
    }

    private int calculateLocalsLimit(Method method) {
        if (method.getVarTable().isEmpty()) {
            return method.isStaticMethod() ? 1 : 1; // at least space for 'this' if not static
        }

        return method.getVarTable().values().stream()
                .mapToInt(var -> var.getVirtualReg())
                .max()
                .orElse(0) + 1;
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // Handle array assignments first
        if (assign.getDest() instanceof ArrayOperand) {
            ArrayOperand arrayDest = (ArrayOperand) assign.getDest();

            // Load array reference directly using the array variable name
            // This avoids infinite recursion by not calling apply() on the ArrayOperand itself
            var arrayReg = currentMethod.getVarTable().get(arrayDest.getName());
            
            // Load the array reference (always an object reference)
            int arrayRegNum = arrayReg.getVirtualReg();
            if (arrayRegNum <= 3) {
                code.append("aload_").append(arrayRegNum).append(NL);
            } else {
                code.append("aload ").append(arrayRegNum).append(NL);
            }

            // Load indices
            for (Element index : arrayDest.getIndexOperands()) {
                code.append(apply((TreeNode) index));
            }

            // Load value to store
            code.append(apply(assign.getRhs()));

            // Store in array
            var elementType = types.getJasminType(assign.getTypeOfAssign());
            if (elementType.equals("I") || elementType.equals("Z")) {
                code.append("iastore").append(NL);
            } else {
                code.append("aastore").append(NL);
            }

            return code.toString();
        }

        // Regular assignment
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        var typeCode = types.getJasminType(lhs.getType());
        int regNum = reg.getVirtualReg();

        // Check for iinc optimization: detect pattern where i = tmp and tmp = i + constant
        if (typeCode.equals("I") && assign.getRhs() instanceof SingleOpInstruction) {
            SingleOpInstruction singleOp = (SingleOpInstruction) assign.getRhs();
            
            if (singleOp.getSingleOperand() instanceof Operand) {
                Operand rhsOperand = (Operand) singleOp.getSingleOperand();
                String tempVarName = rhsOperand.getName();
                
                // Check if this is a temporary variable (starts with "tmp")
                if (tempVarName.startsWith("tmp")) {
                    // Look for the previous instruction that defined this temporary
                    // We need to find the BinaryOpInstruction that assigned to this temp
                    AssignInstruction tempDefining = findPreviousAssignmentForTemp(tempVarName);
                    
                    if (tempDefining != null && tempDefining.getRhs() instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) tempDefining.getRhs();
                        
                        if (binOp.getOperation().getOpType().name().equals("ADD")) {
                            var leftOperand = binOp.getLeftOperand();
                            var rightOperand = binOp.getRightOperand();
                            
                            // Check if one operand is the target variable and the other is a constant
                            boolean leftIsTarget = (leftOperand instanceof Operand) && 
                                                 ((Operand) leftOperand).getName().equals(operand.getName());
                            boolean rightIsTarget = (rightOperand instanceof Operand) && 
                                                  ((Operand) rightOperand).getName().equals(operand.getName());
                            
                            LiteralElement constantOperand = null;
                            if (leftIsTarget && rightOperand instanceof LiteralElement) {
                                constantOperand = (LiteralElement) rightOperand;
                            } else if (rightIsTarget && leftOperand instanceof LiteralElement) {
                                constantOperand = (LiteralElement) leftOperand;
                            }
                            
                            if (constantOperand != null) {
                                try {
                                    int increment = Integer.parseInt(constantOperand.getLiteral());
                                    // Use iinc for small increments (-128 to 127)
                                    if (increment >= -128 && increment <= 127) {
                                        code.append("iinc ").append(regNum).append(" ").append(increment).append(NL);
                                        return code.toString();
                                    }
                                } catch (NumberFormatException e) {
                                    // Not a valid integer, fall through to regular assignment
                                }
                            }
                        }
                    }
                }
            }
        }

        // Regular assignment (not optimizable with iinc)
        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        String storeInst;

        // Use optimized store instructions for registers 0-3
        if (typeCode.equals("I") || typeCode.equals("Z")) {
            if (regNum <= 3) {
                storeInst = "istore_" + regNum;
            } else {
                storeInst = "istore " + regNum;
            }
        } else {
            if (regNum <= 3) {
                storeInst = "astore_" + regNum;
            } else {
                storeInst = "astore " + regNum;
            }
        }

        code.append(storeInst).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        String value = literal.getLiteral();

        // Handle integer literals with optimized instructions
        try {
            int intValue = Integer.parseInt(value);
            if (intValue == -1) {
                return "iconst_m1" + NL;
            } else if (intValue >= 0 && intValue <= 5) {
                return "iconst_" + intValue + NL;
            } else if (intValue >= -128 && intValue <= 127) {
                return "bipush " + intValue + NL;
            } else if (intValue >= -32768 && intValue <= 32767) {
                return "sipush " + intValue + NL;
            } else {
                return "ldc " + intValue + NL;
            }
        } catch (NumberFormatException e) {
            // Not an integer, use ldc
            return "ldc " + value + NL;
        }
    }

    private String generateOperand(Operand operand) {
        // Handle array access
        if (operand instanceof ArrayOperand) {
            ArrayOperand arrayOp = (ArrayOperand) operand;
            var code = new StringBuilder();

            // Load array reference directly using the array variable name
            // This avoids infinite recursion by not calling apply() on the ArrayOperand itself
            var arrayReg = currentMethod.getVarTable().get(arrayOp.getName());
            var arrayTypeCode = types.getJasminType(arrayOp.getType());
            
            // Load the array reference (always an object reference)
            int arrayRegNum = arrayReg.getVirtualReg();
            if (arrayRegNum <= 3) {
                code.append("aload_").append(arrayRegNum).append(NL);
            } else {
                code.append("aload ").append(arrayRegNum).append(NL);
            }

            // Load indices
            for (Element index : arrayOp.getIndexOperands()) {
                code.append(apply((TreeNode) index));
            }

            // Load from array
            var elementType = types.getJasminType(operand.getType());
            if (elementType.equals("I") || elementType.equals("Z")) {
                code.append("iaload").append(NL);
            } else {
                code.append("aaload").append(NL);
            }

            return code.toString();
        }

        // Regular operand
        var reg = currentMethod.getVarTable().get(operand.getName());
        var typeCode = types.getJasminType(operand.getType());

        String loadInst;
        int regNum = reg.getVirtualReg();

        // Use optimized load instructions
        if (typeCode.equals("I") || typeCode.equals("Z")) {
            if (regNum <= 3) {
                loadInst = "iload_" + regNum;
            } else {
                loadInst = "iload " + regNum;
            }
        } else {
            if (regNum <= 3) {
                loadInst = "aload_" + regNum;
            } else {
                loadInst = "aload " + regNum;
            }
        }

        return loadInst + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> {
                code.append(apply(binaryOp.getLeftOperand()));
                code.append(apply(binaryOp.getRightOperand()));
                yield "iadd";
            }
            case SUB -> {
                code.append(apply(binaryOp.getLeftOperand()));
                code.append(apply(binaryOp.getRightOperand()));
                yield "isub";
            }
            case MUL -> {
                code.append(apply(binaryOp.getLeftOperand()));
                code.append(apply(binaryOp.getRightOperand()));
                yield "imul";
            }
            case DIV -> {
                code.append(apply(binaryOp.getLeftOperand()));
                code.append(apply(binaryOp.getRightOperand()));
                yield "idiv";
            }
            case AND -> {
                code.append(apply(binaryOp.getLeftOperand()));
                code.append(apply(binaryOp.getRightOperand()));
                yield "iand";
            }
            case LTH -> {
                // Check for comparison with zero optimization
                var leftOperand = binaryOp.getLeftOperand();
                var rightOperand = binaryOp.getRightOperand();
                
                boolean leftIsZero = isLiteralZero(leftOperand);
                boolean rightIsZero = isLiteralZero(rightOperand);
                
                String trueLabel = "LT_TRUE_" + System.nanoTime();
                String endLabel = "LT_END_" + System.nanoTime();
                
                if (rightIsZero && !leftIsZero) {
                    // variable < 0 -> use iflt
                    code.append(apply(leftOperand));
                    yield """
                    iflt %s
                    iconst_0
                    goto %s
                    %s:
                    iconst_1
                    %s:
                    """.formatted(trueLabel, endLabel, trueLabel, endLabel);
                } else if (leftIsZero && !rightIsZero) {
                    // 0 < variable -> equivalent to variable > 0, use ifgt
                    code.append(apply(rightOperand));
                    yield """
                    ifgt %s
                    iconst_0
                    goto %s
                    %s:
                    iconst_1
                    %s:
                    """.formatted(trueLabel, endLabel, trueLabel, endLabel);
                } else {
                    // Default case: use if_icmplt
                    code.append(apply(leftOperand));
                    code.append(apply(rightOperand));
                    yield """
                    if_icmplt %s
                    iconst_0
                    goto %s
                    %s:
                    iconst_1
                    %s:
                    """.formatted(trueLabel, endLabel, trueLabel, endLabel);
                }
            }
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        if (!binaryOp.getOperation().getOpType().name().equals("LTH")) {
            code.append(op).append(NL);
        } else {
            code.append(op); // already has newlines in multi-line case
        }

        return code.toString();
    }

    private boolean isLiteralZero(Element operand) {
        if (operand instanceof LiteralElement literal) {
            return "0".equals(literal.getLiteral());
        }
        return false;
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (!returnInst.hasReturnValue()) {
            code.append("return").append(NL);
        } else {
            var returnValue = returnInst.getOperand().orElseThrow(() -> new IllegalStateException("Return operand expected"));
            var returnType = types.getJasminType(returnValue.getType());

            code.append(apply(returnValue));

            switch (returnType) {
                case "I", "Z" -> code.append("ireturn").append(NL);
                case "V" -> code.append("return").append(NL);
                default -> code.append("areturn").append(NL);
            }
        }

        return code.toString();
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction invoke) {
        var code = new StringBuilder();

        // Load the object being called on (e.g., 'this' or some object reference)
        code.append(apply(invoke.getCaller()));

        // Load all method arguments
        if (!invoke.getArguments().isEmpty()) {
            for (Element arg : invoke.getArguments()) {
                code.append(apply((TreeNode) arg));
            }
        }

        // Determine method name
        var methodName = ((LiteralElement) invoke.getMethodName()).getLiteral().replace("\"", "");

        // Class where method is defined — assume current class unless it's a field
        var className = ollirResult.getOllirClass().getClassName();

        // Build method descriptor
        var descriptor = new StringBuilder("(");
        if (!invoke.getArguments().isEmpty()) {
            for (Element arg : invoke.getArguments()) {
                descriptor.append(types.getJasminType(arg.getType()));
            }
        }
        descriptor.append(")");
        descriptor.append(types.getJasminType(invoke.getReturnType()));

        code.append("invokevirtual ")
                .append(className).append("/")
                .append(methodName)
                .append(descriptor)
                .append(NL);

        return code.toString();
    }

    private String generateInvokeSpecial(InvokeSpecialInstruction invoke) {
        var code = new StringBuilder();

        // Load the object being called on (usually 'this' for constructors)
        code.append(apply(invoke.getCaller()));

        // Load all method arguments
        if (!invoke.getArguments().isEmpty()) {
            for (Element arg : invoke.getArguments()) {
                code.append(apply((TreeNode) arg));
            }
        }

        // Determine method name
        var methodName = ((LiteralElement) invoke.getMethodName()).getLiteral().replace("\"", "");

        // For constructor calls, the class name comes from the caller's type
        String className;
        if ("<init>".equals(methodName)) {
            var callerType = invoke.getCaller().getType();
            if (callerType.toString().contains(ollirResult.getOllirClass().getClassName())) {
                className = ollirResult.getOllirClass().getClassName();
            } else {
                className = types.getJasminType(callerType).replaceAll("^L|;$", "");
            }
        } else {
            className = ollirResult.getOllirClass().getClassName();
        }

        // Build method descriptor
        var descriptor = new StringBuilder("(");
        if (!invoke.getArguments().isEmpty()) {
            for (Element arg : invoke.getArguments()) {
                descriptor.append(types.getJasminType(arg.getType()));
            }
        }
        descriptor.append(")");
        descriptor.append(types.getJasminType(invoke.getReturnType()));

        code.append("invokespecial ")
                .append(className).append("/")
                .append(methodName)
                .append(descriptor)
                .append(NL);

        return code.toString();
    }

    private String generateNew(NewInstruction newInst) {
        var code = new StringBuilder();

        // Get the return type which tells us what we're creating
        var returnType = newInst.getReturnType();
        
        // Check if this is an array creation by examining the return type
        // Array types will have the form "INT32[]", "BOOLEAN[]", etc.
        if (returnType.toString().endsWith("[]")) {
            // This is array creation
            if (!newInst.getArguments().isEmpty()) {
                // Load the array size (first and only argument)
                code.append(apply((TreeNode) newInst.getArguments().get(0)));
            }

            // Generate appropriate newarray instruction based on element type
            String typeStr = returnType.toString();
            if (typeStr.startsWith("INT32") || typeStr.contains("int")) {
                code.append("newarray int").append(NL);
            } else if (typeStr.startsWith("BOOLEAN") || typeStr.contains("boolean")) {
                code.append("newarray boolean").append(NL);
            } else {
                // For object arrays, extract the object type
                String objType = types.getJasminType(returnType).replaceAll("\\[", "");
                code.append("anewarray ").append(objType).append(NL);
            }
            
            return code.toString();
        }
        
        // Regular object creation
        String className = types.getJasminType(returnType).replaceAll("^L|;$", "");
        code.append("new ").append(className).append(NL);
        code.append("dup").append(NL); // Duplicate reference for constructor call

        return code.toString();
    }

    private String generateCall(CallInstruction call) {
        // This method handles generic calls and delegates to specific methods
        if (call instanceof InvokeVirtualInstruction) {
            return generateInvokeVirtual((InvokeVirtualInstruction) call);
        } else if (call instanceof InvokeSpecialInstruction) {
            return generateInvokeSpecial((InvokeSpecialInstruction) call);
        } else if (call instanceof NewInstruction) {
            return generateNew((NewInstruction) call);
        } else if (call instanceof ArrayLengthInstruction) {
            return generateArrayLength((ArrayLengthInstruction) call);
        } else {
            throw new NotImplementedException("Call instruction type: " + call.getClass());
        }
    }

    private String generateArrayLength(ArrayLengthInstruction arrayLength) {
        var code = new StringBuilder();
        
        // Load the array reference onto the stack
        // The array operand is stored as the caller in ArrayLengthInstruction
        code.append(apply(arrayLength.getCaller()));
        
        // Generate the arraylength instruction
        code.append("arraylength").append(NL);
        
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();

        // Load the object reference (usually 'this')
        // The object is the first operand according to FieldInstruction
        code.append(apply(getField.getObject()));

        // Get field name from the second operand (field operand)
        String fieldName = getField.getField().getName();

        // Get field type from the instruction's field type
        String fieldType = types.getJasminType(getField.getFieldType());

        // Get class name (usually current class)
        String className = ollirResult.getOllirClass().getClassName();

        code.append("getfield ")
                .append(className).append("/")
                .append(fieldName).append(" ")
                .append(fieldType)
                .append(NL);

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putField) {
        var code = new StringBuilder();

        // Load the object reference (usually 'this')
        // The object is the first operand according to FieldInstruction
        code.append(apply(putField.getObject()));

        // Load the value to store (third operand - additional operands beyond object and field)
        List<Element> operands = putField.getOperands();
        if (operands.size() > 2) {
            // The value to store should be the third operand
            code.append(apply((TreeNode) operands.get(2)));
        }

        // Get field name from the second operand (field operand)
        String fieldName = putField.getField().getName();

        // Get field type from the instruction's field type
        String fieldType = types.getJasminType(putField.getFieldType());

        // Get class name (usually current class)
        String className = ollirResult.getOllirClass().getClassName();

        code.append("putfield ")
                .append(className).append("/")
                .append(fieldName).append(" ")
                .append(fieldType)
                .append(NL);

        return code.toString();
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        // Load the operand
        code.append(apply(unaryOp.getOperand()));

        // Handle the unary operation
        var op = switch (unaryOp.getOperation().getOpType()) {
            case NOTB -> {
                // Boolean NOT operation: flip 0 to 1 and 1 to 0
                // We can use iconst_1 followed by ixor to flip the boolean value
                yield "iconst_1" + NL + "ixor";
            }
            default -> throw new NotImplementedException(unaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    /**
     * Find the previous assignment that defined a temporary variable.
     * This is used for iinc optimization to detect patterns like:
     * tmp0 = i + 1
     * i = tmp0
     */
    private AssignInstruction findPreviousAssignmentForTemp(String tempVarName) {
        List<Instruction> instructions = currentMethod.getInstructions();
        
        // Look through the instructions in reverse order to find the most recent assignment
        for (int i = instructions.size() - 1; i >= 0; i--) {
            Instruction inst = instructions.get(i);
            
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                Element dest = assign.getDest();
                
                if (dest instanceof Operand) {
                    Operand operand = (Operand) dest;
                    if (operand.getName().equals(tempVarName)) {
                        return assign;
                    }
                }
            }
        }
        
        return null;
    }
}
