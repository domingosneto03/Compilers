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
        // Conservative estimation - could be more sophisticated
        int maxStack = 0;
        int currentStack = 0;

        for (var inst : method.getInstructions()) {
            switch (inst.getInstType()) {
                case ASSIGN:
                    AssignInstruction assign = (AssignInstruction) inst;
                    if (assign.getRhs() instanceof BinaryOpInstruction) {
                        currentStack += 2; // two operands
                        maxStack = Math.max(maxStack, currentStack);
                        currentStack -= 1; // result
                    } else if (assign.getRhs() instanceof GetFieldInstruction) {
                        currentStack += 1; // object reference
                        maxStack = Math.max(maxStack, currentStack);
                        currentStack = 1; // field value remains
                    } else if (assign.getRhs() instanceof CallInstruction) {
                        CallInstruction call = (CallInstruction) assign.getRhs();
                        // Object + arguments
                        currentStack += 1 + call.getArguments().size();
                        maxStack = Math.max(maxStack, currentStack);
                        currentStack = 1; // result remains
                    } else {
                        currentStack += 1; // simple load
                        maxStack = Math.max(maxStack, currentStack);
                    }
                    currentStack -= 1; // store pops value
                    break;
                case CALL:
                    CallInstruction call = (CallInstruction) inst;
                    currentStack += 1 + call.getArguments().size();
                    maxStack = Math.max(maxStack, currentStack);
                    currentStack = 0; // call consumes all
                    break;
                case GETFIELD:
                    currentStack += 1; // object reference
                    maxStack = Math.max(maxStack, currentStack);
                    currentStack = 1; // field value remains
                    break;
                case PUTFIELD:
                    currentStack += 2; // object reference + value
                    maxStack = Math.max(maxStack, currentStack);
                    currentStack = 0; // putfield consumes both
                    break;
                case RETURN:
                    currentStack += 1;
                    maxStack = Math.max(maxStack, currentStack);
                    break;
                case BRANCH:
                    if (inst instanceof CondBranchInstruction) {
                        CondBranchInstruction condBranch = (CondBranchInstruction) inst;
                        currentStack += condBranch.getOperands().size();
                        maxStack = Math.max(maxStack, currentStack);
                        currentStack = 0; // branch consumes operands
                    }
                    break;
                case GOTO:
                    // goto doesn't affect stack
                    break;
                default:
                    currentStack += 1;
                    maxStack = Math.max(maxStack, currentStack);
                    break;
            }
        }

        return Math.max(maxStack, 2); // minimum reasonable stack
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

            // Load array reference
            code.append(apply(arrayDest));

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
        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        var typeCode = types.getJasminType(lhs.getType());

        String storeInst = switch (typeCode) {
            case "I", "Z" -> "istore";
            default -> "astore";
        };

        code.append(storeInst).append(" ").append(reg.getVirtualReg()).append(NL);

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

            // Load array reference
            code.append(apply(arrayOp));

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

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case AND -> "iand";
            case LTH -> {
                // Comparison result handling
                String trueLabel = "LT_TRUE_" + System.nanoTime();
                String endLabel = "LT_END_" + System.nanoTime();
                yield """
                if_icmplt %s
                iconst_0
                goto %s
                %s:
                iconst_1
                %s:
                """.formatted(trueLabel, endLabel, trueLabel, endLabel);
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

        if (returnType.toString().contains("array")) {
            // Array creation - load size argument first
            if (!newInst.getArguments().isEmpty()) {
                code.append(apply((TreeNode) newInst.getArguments().get(0))); // Load array size
            }

            if (returnType.toString().contains("int") || returnType.toString().contains("i32")) {
                code.append("newarray int").append(NL);
            } else {
                // For object arrays
                String objType = types.getJasminType(returnType).replaceAll("\\[", "");
                code.append("anewarray ").append(objType).append(NL);
            }
        } else {
            // Object creation
            String className = types.getJasminType(returnType).replaceAll("^L|;$", "");
            code.append("new ").append(className).append(NL);
            code.append("dup").append(NL); // Duplicate reference for constructor call
        }

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
        } else {
            throw new NotImplementedException("Call instruction type: " + call.getClass());
        }
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
}