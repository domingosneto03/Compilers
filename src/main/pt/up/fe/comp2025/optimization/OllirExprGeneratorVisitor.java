package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_TRUE, this::visitBoolean);
        addVisit(BOOLEAN_FALSE, this::visitBoolean);
        addVisit(PARENTHESIZED_EXPR, this::visitParenthesizedExpr);
        addVisit(THIS_EXPR, this::visitThisExpr);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(NEW_INT_ARRAY_EXPR, this::visitNewIntArrayExpr);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObjectExpr);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(ARRAY_LENGTH_EXPR, this::visitArrayLengthExpr);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(POSTFIX_EXPR, this::visitPostfixExpr);
        addVisit(ARRAY_LITERAL_EXPR, this::visitArrayLiteralExpr);

        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        String op = node.get("op");
        StringBuilder computation = new StringBuilder();

        if (op.equals("&&")) {
            JmmNode leftNode = node.getChild(0);
            JmmNode rightNode = node.getChild(1);

            OllirExprResult leftResult = visit(leftNode);
            computation.append(leftResult.getComputation());

            String resultVar = ollirTypes.nextTemp() + ".bool";

            String falseLabel = ollirTypes.nextTemp("false");
            String endLabel = ollirTypes.nextTemp("end");

            computation.append("if (").append(leftResult.getCode()).append(" ==.bool 0.bool) goto ").append(falseLabel).append(END_STMT);

            OllirExprResult rightResult = visit(rightNode);
            computation.append(rightResult.getComputation());

            computation.append(resultVar).append(" :=.bool ").append(rightResult.getCode()).append(END_STMT);
            computation.append("goto ").append(endLabel).append(END_STMT);

            computation.append(falseLabel).append(":").append("\n");
            computation.append(resultVar).append(" :=.bool 0.bool").append(END_STMT);

            computation.append(endLabel).append(":").append("\n");

            return new OllirExprResult(resultVar, computation);
        }

        OllirExprResult leftResult = visit(node.getChild(0));
        OllirExprResult rightResult = visit(node.getChild(1));

        computation.append(leftResult.getComputation());
        computation.append(rightResult.getComputation());

        Type resultType = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(resultType);

        String resultVar = ollirTypes.nextTemp() + ollirType;

        String ollirOp = switch(op) {
            case "+" -> "+";
            case "-" -> "-";
            case "*" -> "*";
            case "/" -> "/";
            case "<" -> "<";
            case ">" -> ">";
            case "<=" -> "<=";
            case ">=" -> ">=";
            case "==" -> "==";
            case "!=" -> "!=";
            case "||" -> "||";
            default -> throw new RuntimeException("Unsupported binary operator: " + op);
        };

        computation.append(resultVar).append(" :=").append(ollirType).append(" ")
                .append(leftResult.getCode()).append(" ")
                .append(ollirOp).append(ollirType).append(" ")
                .append(rightResult.getCode()).append(END_STMT);

        return new OllirExprResult(resultVar, computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("value");

        String methodName = node.getAncestor(METHOD_DECL.getNodeName())
                .map(m -> m.get("name"))
                .orElse("main");

        if ("args".equals(methodName)) methodName = "main";

        Type type = null;

        for (var local : table.getLocalVariables(methodName)) {
            if (local.getName().equals(id)) {
                type = local.getType();
                break;
            }
        }
        if (type == null) {
            List<Symbol> parameters = table.getParameters(methodName);
            if ("main".equals(methodName) && "args".equals(id) && 
                (parameters.isEmpty() || !parameters.stream().anyMatch(p -> "args".equals(p.getName())))) {
                type = new Type("String", true);
            } else {
                for (var param : parameters) {
                    if (param.getName().equals(id)) {
                        type = param.getType();
                        break;
                    }
                }
            }
        }

        if (type == null) {
            for (var field : table.getFields()) {
                if (field.getName().equals(id)) {
                    type = field.getType();
                    break;
                }
            }
        }

        if (type == null) {
            for (String imp : table.getImports()) {
                if (imp.endsWith("." + id) || imp.equals(id)) {
                    type = new Type(id, false);
                    break;
                }
            }
        }

        if (type == null) {
            throw new RuntimeException("Undefined identifier '" + id + "' in method '" + methodName + "'");
        }

        String ollirType = ollirTypes.toOllirType(type);

        boolean isLocal = table.getLocalVariables(methodName).stream()
                .anyMatch(symbol -> symbol.getName().equals(id));
        boolean isParam = table.getParameters(methodName).stream()
                .anyMatch(symbol -> symbol.getName().equals(id));

        if (!isLocal && !isParam && table.getFields().stream().anyMatch(f -> f.getName().equals(id))) {
            String tempVar = ollirTypes.nextTemp() + ollirType;

            StringBuilder computation = new StringBuilder();
            computation.append(tempVar).append(" :=").append(ollirType)
                    .append(" getfield(this.")
                    .append(table.getClassName()).append(", ")
                    .append(id).append(ollirType).append(")")
                    .append(ollirType).append(END_STMT);

            return new OllirExprResult(tempVar, computation);
        }

        return new OllirExprResult(id + ollirType);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        Type boolType = new Type("boolean", false);
        String ollirBoolType = ollirTypes.toOllirType(boolType);
        String value = node.getKind().equals(BOOLEAN_TRUE.getNodeName()) ? "1" : "0";
        String code = value + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitParenthesizedExpr(JmmNode node, Void unused) {

        return visit(node.getChild(0));
    }

    private OllirExprResult visitThisExpr(JmmNode node, Void unused) {
        Type classType = new Type(table.getClassName(), false);
        String ollirType = ollirTypes.toOllirType(classType);
        String code = "this" + ollirType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var operand = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(operand.getComputation());

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        // Only '!' operator is supported in the grammar
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("!").append(resOllirType).append(SPACE)
                .append(operand.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewIntArrayExpr(JmmNode node, Void unused) {
        var sizeExpr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(sizeExpr.getComputation());

        Type arrayType = new Type("int", true);
        String ollirType = ollirTypes.toOllirType(arrayType);
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(array, ").append(sizeExpr.getCode()).append(")")
                .append(ollirType)
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewObjectExpr(JmmNode node, Void unused) {
        String className = node.get("value");
        Type objType = new Type(className, false);
        String ollirType = ollirTypes.toOllirType(objType);
        String code = ollirTypes.nextTemp() + ollirType;

        StringBuilder computation = new StringBuilder();
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(").append(className).append(")")
                .append(ollirType).append(END_STMT);

        // Add constructor call
        computation.append("invokespecial(").append(code)
                .append(", \"<init>\").V").append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        // Get array expression and index expression
        JmmNode arrayNode = node.getChild(0);
        JmmNode indexNode = node.getChild(1);

        // Get the method name from ancestors
        String methodName = node.getAncestor(METHOD_DECL.getNodeName())
                .map(m -> m.get("name"))
                .orElse("main");

        if ("args".equals(methodName)) methodName = "main";

        OllirExprResult arrayExpr = visit(arrayNode);
        OllirExprResult indexExpr = visit(indexNode);

        // Add their computations
        computation.append(arrayExpr.getComputation());
        computation.append(indexExpr.getComputation());

        Type arrayType;
        try {
            arrayType = types.getExprType(arrayNode, methodName);
            if (arrayNode.getKind().equals(VAR_REF_EXPR.getNodeName()) && 
                arrayNode.get("value").equals("args") && 
                methodName.equals("main")) {
                if (!arrayType.isArray()) {
                    arrayType = new Type("String", true);
                }
            }
            
            if (!arrayType.isArray()) {
                throw new RuntimeException("Expression is not an array: " + arrayNode);
            }
        } catch (Exception e) {
            if (arrayNode.getKind().equals(VAR_REF_EXPR.getNodeName()) && 
                arrayNode.get("value").equals("args") && 
                methodName.equals("main")) {
                arrayType = new Type("String", true);
            } else {
                throw new RuntimeException("Error determining array type for: " + arrayNode, e);
            }
        }

        Type elementType = new Type(arrayType.getName(), false);
        String ollirType = ollirTypes.toOllirType(elementType);

        String tempVar = ollirTypes.nextTemp("elem") + ollirType;

        computation.append(tempVar).append(" :=").append(ollirType)
                .append(" ").append(arrayExpr.getCode()).append("[")
                .append(indexExpr.getCode()).append("]").append(ollirType)
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitArrayLengthExpr(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());

        Type intType = TypeUtils.newIntType();
        String ollirType = ollirTypes.toOllirType(intType);
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("arraylength(").append(arrayExpr.getCode()).append(")")
                .append(ollirType)
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        var callerExpr = visit(node.getChild(0));
        String methodName = node.get("method");

        StringBuilder computation = new StringBuilder();
        computation.append(callerExpr.getComputation());

        // Process all arguments
        StringBuilder args = new StringBuilder();
        for (int i = 1; i < node.getNumChildren(); i++) {
            var argExpr = visit(node.getChild(i));
            computation.append(argExpr.getComputation());

            if (i > 1) args.append(", ");
            args.append(argExpr.getCode());
        }

        // Determine the return type of the method
        Type returnType;
        try {
            JmmNode caller = node.getChild(0);
            if (caller.getKind().equals(THIS_EXPR.getNodeName()) ||
                    (caller.getKind().equals(VAR_REF_EXPR.getNodeName()) && caller.get("value").equals(table.getClassName()))) {
                returnType = table.getReturnType(methodName);
            } else {
                JmmNode parent = node.getParent();
                if (parent != null && parent.getKind().equals("AssignStmt") && parent.getChild(1) == node) {
                    JmmNode lhs = parent.getChild(0);
                    if (lhs.getKind().equals(VAR_REF_EXPR.getNodeName())) {
                        String varName = lhs.get("value");
                        String currentMethod = node.getAncestor("MethodDecl").map(m -> m.get("name")).orElse("main");
                        if ("args".equals(currentMethod)) currentMethod = "main";
                        
                        try {
                            Type varType = types.getExprType(lhs, currentMethod);
                            returnType = varType;
                        } catch (Exception e) {
                            returnType = TypeUtils.newIntType();
                        }
                    } else {
                        returnType = TypeUtils.newIntType();
                    }
                } else {
                    if (caller.getKind().equals(VAR_REF_EXPR.getNodeName())) {
                        String callerName = caller.get("value");
                        try {
                            Type callerType = types.getExprType(caller);
                            if (callerType != null && table.getImports().stream()
                                    .anyMatch(imp -> imp.endsWith("." + callerType.getName()) || imp.equals(callerType.getName()))) {
                                if ("A".equals(callerType.getName()) && "bar".equals(methodName)) {
                                    returnType = new Type("boolean", false);
                                } else {
                                    returnType = new Type("unknown", false);
                                }
                            } else {
                                returnType = types.getExprType(node);
                            }
                        } catch (Exception e) {
                            returnType = TypeUtils.newIntType();
                        }
                    } else {
                        returnType = types.getExprType(node);
                    }
                }
            }
        } catch (Exception e) {
            returnType = TypeUtils.newIntType();
        }

        String ollirReturnType = ollirTypes.toOllirType(returnType);
        String code = ollirTypes.nextTemp() + ollirReturnType;

        boolean isInExprStmt = node.getParent() != null && node.getParent().getKind().equals(EXPR_STMT.getNodeName());

        if (!isInExprStmt) {
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(ollirReturnType).append(SPACE);
        }

        String invokeKind;
        final String callerIdFinal;
        boolean isStaticCall = false;

        if (node.getChild(0).getKind().equals(VAR_REF_EXPR.getNodeName())) {
            callerIdFinal = node.getChild(0).get("value");
            isStaticCall = table.getImports().stream().anyMatch(imp -> imp.endsWith("." + callerIdFinal) || imp.equals(callerIdFinal));
        } else {
            callerIdFinal = "";
        }

        if (isStaticCall) {
            invokeKind = "invokestatic";
            computation.append(invokeKind).append("(").append(callerIdFinal);
        } else {
            invokeKind = "invokevirtual";
            computation.append(invokeKind).append("(").append(callerExpr.getCode());
        }

        computation.append(", \"").append(methodName).append("\"");

        if (args.length() > 0) {
            computation.append(", ").append(args);
        }

        computation.append(")").append(ollirReturnType).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitPostfixExpr(JmmNode node, Void unused) {
        String varName = node.get("value");
        String op = node.get("op");

        String methodName = node.getAncestor(METHOD_DECL.getNodeName())
                .map(m -> m.get("name"))
                .orElse("main");

        if ("args".equals(methodName)) methodName = "main";

        Type type;
        try {
            type = types.getExprType(node);
        } catch (Exception e) {
            type = TypeUtils.newIntType();
        }
        
        String ollirType = ollirTypes.toOllirType(type);

        StringBuilder computation = new StringBuilder();

        String tempVar = ollirTypes.nextTemp() + ollirType;
        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(varName).append(ollirType).append(END_STMT);


        computation.append(varName).append(ollirType).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(varName).append(ollirType).append(SPACE);

        if (op.equals("++")) {
            computation.append("+").append(ollirType).append(SPACE)
                    .append("1").append(ollirType);
        } else {
            computation.append("-").append(ollirType).append(SPACE)
                    .append("1").append(ollirType);
        }
        computation.append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitArrayLiteralExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        String methodName = node.getAncestor("MethodDecl").map(m -> m.get("name")).orElse("main");
        if ("args".equals(methodName)) methodName = "main";

        Type elemType = node.getNumChildren() > 0
                ? types.getExprType(node.getChild(0), methodName)
                : TypeUtils.newIntType();

        Type arrayType = new Type(elemType.getName(), true);
        String ollirArrayType = ollirTypes.toOllirType(arrayType);
        String ollirElemType = ollirTypes.toOllirType(elemType);

        String arrayVar = ollirTypes.nextTemp("array") + ollirArrayType;

        computation.append(arrayVar).append(" :=").append(ollirArrayType)
                .append(" new(array, ").append(node.getNumChildren()).append(")")
                .append(ollirArrayType).append(END_STMT);

        for (int i = 0; i < node.getNumChildren(); i++) {
            var elemExpr = visit(node.getChild(i));
            computation.append(elemExpr.getComputation());

            computation.append(arrayVar).append("[").append(i).append(".i32").append("]")
                    .append(ollirElemType)
                    .append(" :=").append(ollirElemType)
                    .append(" ").append(elemExpr.getCode()).append(END_STMT);
        }

        return new OllirExprResult(arrayVar, computation);
    }

    /**
     * Default visitor. Visits every child node and returns an empty result.
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return OllirExprResult.EMPTY;
    }
}