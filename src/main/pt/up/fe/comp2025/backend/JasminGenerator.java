package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.BuiltinKind;
import org.specs.comp.ollir.type.BuiltinType;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;
import org.specs.comp.ollir.type.ArrayType;


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
        generators.put(ArrayLengthInstruction.class, this::generateArrayLength);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);


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
        int maxStack = 0;
        int currentStack = 0;

        for (Instruction inst : method.getInstructions()) {
            int before = currentStack;

            switch (inst.getInstType()) {
                case ASSIGN -> {
                    var rhs = ((AssignInstruction) inst).getRhs();

                    if (rhs instanceof BinaryOpInstruction) {
                        currentStack += 2; // left + right
                        currentStack -= 1; // result replaces both
                    } else if (rhs instanceof UnaryOpInstruction) {
                        currentStack += 1; // operand
                        // stays 1
                    } else if (rhs instanceof CallInstruction call) {
                        int argCount = (call.getCaller() != null ? 1 : 0) + call.getArguments().size();
                        currentStack += argCount;
                        currentStack -= argCount; // all args consumed
                        if (!(call instanceof InvokeStaticInstruction)) {
                            currentStack += 1; // result stays
                        }
                    } else {
                        currentStack += 1;
                    }

                    currentStack -= 1; // istore/astore
                }

                case CALL -> {
                    CallInstruction call = (CallInstruction) inst;
                    int argCount = (call.getCaller() != null ? 1 : 0) + call.getArguments().size();
                    currentStack += argCount;
                    currentStack -= argCount;
                    currentStack += 1; // return value (if any)
                }

                case BRANCH -> {
                    if (inst instanceof CondBranchInstruction cond) {
                        Instruction condInst = cond.getCondition();
                        if (condInst instanceof BinaryOpInstruction) {
                            currentStack += 2;
                            currentStack -= 2;
                        } else if (condInst instanceof UnaryOpInstruction) {
                            currentStack += 1;
                            currentStack -= 1;
                        } else {
                            currentStack += cond.getOperands().size();
                            currentStack -= cond.getOperands().size();
                        }
                    }
                }

                case RETURN -> {
                    if (((ReturnInstruction) inst).hasReturnValue()) {
                        currentStack += 1;
                        currentStack -= 1;
                    }
                }

                case PUTFIELD -> {
                    currentStack += 2;
                    currentStack -= 2;
                }

                case GETFIELD -> {
                    currentStack += 1;
                    currentStack -= 1;
                    currentStack += 1;
                }

                case GOTO -> {} // no stack change

                default -> {
                    currentStack += 1;
                    currentStack -= 1;
                }
            }

            maxStack = Math.max(maxStack, currentStack);
            currentStack = Math.max(0, currentStack); // ensure it doesn't go negative
        }

        return Math.max(4, maxStack); // base safe limit
    }




    private int estimateConditionDepth(Instruction condition) {
        if (condition instanceof BinaryOpInstruction bin) {
            return 2; // two operands
        }
        if (condition instanceof UnaryOpInstruction) {
            return 1;
        }
        return 1;
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

        // Special case: x = x + 1  or x = x - 1  -> use iinc
        if (assign.getRhs() instanceof BinaryOpInstruction binOp &&
                binOp.getLeftOperand() instanceof Operand left &&
                binOp.getRightOperand() instanceof LiteralElement right &&
                assign.getDest() instanceof Operand dest &&
                left.getName().equals(dest.getName())) {

            String literal = right.getLiteral();
            String opType = binOp.getOperation().getOpType().name();
            int amount = opType.equals("ADD") ? 1 : opType.equals("SUB") ? -1 : 0;

            if ((amount == 1 || amount == -1) && literal.equals("1")) {
                int reg = currentMethod.getVarTable().get(dest.getName()).getVirtualReg();
                return "iinc " + reg + " " + amount + NL;
            }
        }

        // Handle array assignments first
        if (assign.getDest() instanceof ArrayOperand arrayDest) {
            // Load array reference
            var reg = currentMethod.getVarTable().get(arrayDest.getName());
            code.append("aload ").append(reg.getVirtualReg()).append(NL);

            // Load index (only single-dimensional arrays assumed)
            code.append(apply(arrayDest.getIndexOperands().get(0)));

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

        int regNum = reg.getVirtualReg();

        if (typeCode.equals("I") || typeCode.equals("Z")) {
            switch (regNum) {
                case 0 -> code.append("istore_0").append(NL);
                case 1 -> code.append("istore_1").append(NL);
                case 2 -> code.append("istore_2").append(NL);
                case 3 -> code.append("istore_3").append(NL);
                default -> code.append("istore ").append(regNum).append(NL);
            }
        } else {
            switch (regNum) {
                case 0 -> code.append("astore_0").append(NL);
                case 1 -> code.append("astore_1").append(NL);
                case 2 -> code.append("astore_2").append(NL);
                case 3 -> code.append("astore_3").append(NL);
                default -> code.append("astore ").append(regNum).append(NL);
            }
        }


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
        if (operand instanceof ArrayOperand arrayOp) {
            return generateArrayAccess(arrayOp, true); // true = read (iaload)
        }

        var reg = currentMethod.getVarTable().get(operand.getName());
        var typeCode = types.getJasminType(operand.getType());

        int regNum = reg.getVirtualReg();

        if (typeCode.equals("I") || typeCode.equals("Z")) {
            return switch (regNum) {
                case 0 -> "iload_0" + NL;
                case 1 -> "iload_1" + NL;
                case 2 -> "iload_2" + NL;
                case 3 -> "iload_3" + NL;
                default -> "iload " + regNum + NL;
            };
        } else {
            return switch (regNum) {
                case 0 -> "aload_0" + NL;
                case 1 -> "aload_1" + NL;
                case 2 -> "aload_2" + NL;
                case 3 -> "aload_3" + NL;
                default -> "aload " + regNum + NL;
            };
        }
    }

    private String generateArrayAccess(ArrayOperand arrayOp, boolean isLoad) {
        var code = new StringBuilder();

        // Load array reference
        var baseReg = currentMethod.getVarTable().get(arrayOp.getName());
        code.append("aload ").append(baseReg.getVirtualReg()).append(NL);

        // Load index (assumes single-dimensional array access)
        code.append(apply(arrayOp.getIndexOperands().get(0)));

        // Emit correct Jasmin instruction
        if (isLoad) {
            code.append("iaload").append(NL);  // Read array value
        } else {
            code.append("iastore").append(NL); // Write to array
        }

        return code.toString();
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
                // Check if right operand is 0
                if (binaryOp.getRightOperand() instanceof LiteralElement literal &&
                        literal.getLiteral().equals("0")) {

                    // Only load left operand
                    var left = apply(binaryOp.getLeftOperand());
                    String trueLabel = "LT_TRUE_" + System.nanoTime();
                    String endLabel = "LT_END_" + System.nanoTime();

                    yield left +
                            "iflt " + trueLabel + NL +
                            "iconst_0" + NL +
                            "goto " + endLabel + NL +
                            trueLabel + ":" + NL +
                            "iconst_1" + NL +
                            endLabel + ":" + NL;
                }

                // Default full compare (x < y)
                String trueLabel = "LT_TRUE_" + System.nanoTime();
                String endLabel = "LT_END_" + System.nanoTime();

                yield apply(binaryOp.getLeftOperand()) +
                        apply(binaryOp.getRightOperand()) +
                        "if_icmplt " + trueLabel + NL +
                        "iconst_0" + NL +
                        "goto " + endLabel + NL +
                        trueLabel + ":" + NL +
                        "iconst_1" + NL +
                        endLabel + ":" + NL;
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
        var returnType = newInst.getReturnType();

        if (returnType instanceof ArrayType arrayType) {
            // Load size of the array
            if (!newInst.getArguments().isEmpty()) {
                code.append(apply((TreeNode) newInst.getArguments().get(0)));
            }

            // Determine if it's a primitive int array or an object array
            var elementType = arrayType.getElementType();
            if (elementType instanceof BuiltinType builtin && builtin.getKind() == BuiltinKind.INT32) {
                code.append("newarray int").append(NL);
            } else {
                var objType = types.getJasminType(elementType).replaceAll("^L|;$", "");
                code.append("anewarray ").append(objType).append(NL);
            }
        } else {
            // Object creation
            String className = types.getJasminType(returnType).replaceAll("^L|;$", "");
            code.append("new ").append(className).append(NL);
            code.append("dup").append(NL);
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
        } else if (call instanceof ArrayLengthInstruction) {
            return generateArrayLength((ArrayLengthInstruction) call);
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

    // Add this new method to handle array length:
    private String generateArrayLength(ArrayLengthInstruction arrayLength) {
        var code = new StringBuilder();

        // Load the array reference using getCaller() instead of getFirstOperand()
        code.append(apply(arrayLength.getCaller()));

        // Get array length
        code.append(TAB).append("arraylength").append(NL);

        return code.toString();
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        code.append(apply(unaryOp.getOperand()));

        switch (unaryOp.getOperation().getOpType()) {
            case NOT, NOTB -> {
                String trueLabel = "NOT_TRUE_" + System.nanoTime();
                String endLabel = "NOT_END_" + System.nanoTime();

                code.append("ifeq ").append(trueLabel).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto ").append(endLabel).append(NL);
                code.append(trueLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append(endLabel).append(":").append(NL);
            }

            default -> throw new NotImplementedException("Unary op " + unaryOp.getOperation().getOpType());
        }

        return code.toString();
    }


}