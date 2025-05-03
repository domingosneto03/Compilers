package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;

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

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("value");
        String methodName = node.getAncestor(METHOD_DECL.getNodeName()).map(m -> m.get("name")).orElse("main");
        Type type = types.getExprType(node, methodName);
        String ollirType = ollirTypes.toOllirType(type);

        // Check if the referenced variable is a parameter or local variable in the method
        boolean isParam = table.getParameters(methodName) != null &&
                table.getParameters(methodName).stream().anyMatch(s -> s.getName().equals(id));
        boolean isLocalVar = table.getLocalVariables(methodName) != null &&
                table.getLocalVariables(methodName).stream().anyMatch(s -> s.getName().equals(id));
        // Check if it's a field of the class
        boolean isField = table.getFields().stream().anyMatch(f -> f.getName().equals(id));

        // If it's not a parameter or local variable but it is a field, generate a getfield instruction
        if (isField && !isParam && !isLocalVar) {
            String className = table.getClassName();
            String code = "getfield(this." + className + ", " + id + ollirType + ")" + ollirType;
            return new OllirExprResult(code);
        } else if (table.getImports().stream().anyMatch(imp -> imp.endsWith("." + id) || imp.equals(id))) {
            // If it's an imported class/package, just return the id
            return new OllirExprResult(id);
        }

        // Otherwise, it's a local variable or parameter reference
        return new OllirExprResult(id + ollirType);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        String value = node.getKind().equals("BooleanTrue") ? "1" : "0";
        String code = value + ".bool";
        return new OllirExprResult(code);
    }

    private OllirExprResult visitParenthesizedExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitThisExpr(JmmNode node, Void unused) {
        String code = "this." + table.getClassName();
        return new OllirExprResult(code);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var operand = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(operand.getComputation());
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("!.bool ").append(operand.getCode()).append(END_STMT);
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));
        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());
        String op = node.get("op");
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE)
                .append(op).append(resOllirType).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewIntArrayExpr(JmmNode node, Void unused) {
        var sizeExpr = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(sizeExpr.getComputation());
        String code = "new(array, " + sizeExpr.getCode() + ").array.i32";
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewObjectExpr(JmmNode node, Void unused) {
        String className = node.get("value");
        String code = "new(" + className + ")." + className;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));
        var indexExpr = visit(node.getChild(1));
        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());
        computation.append(indexExpr.getComputation());
        Type elemType = types.getExprType(node, node.getAncestor("MethodDecl").map(m -> m.get("name")).orElse("main"));
        String elemOllirType = ollirTypes.toOllirType(elemType);
        String code = arrayExpr.getCode() + "[" + indexExpr.getCode() + "]" + elemOllirType;
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayLengthExpr(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());
        String code = arrayExpr.getCode() + ".length";
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder argsComputation = new StringBuilder();
        List<String> argCodes = new ArrayList<>();

        // Get method name
        String method = node.get("method");

        // Process the caller expression (first child)
        JmmNode caller = node.getChild(0);
        OllirExprResult callerResult = visit(caller);
        computation.append(callerResult.getComputation());

        // Process argument expressions
        for (int i = 1; i < node.getNumChildren(); i++) {
            OllirExprResult argResult = visit(node.getChild(i));
            argsComputation.append(argResult.getComputation());
            argCodes.add(argResult.getCode());
        }
        computation.append(argsComputation);

        // Check if the method call is to an imported class
        String callerName;
        if (caller.getKind().equals(VAR_REF_EXPR.getNodeName())) {
            callerName = caller.get("value");
        } else {
            callerName = "";
        }

        // Determine return type
        String methodName = node.getAncestor(METHOD_DECL.getNodeName()).map(m -> m.get("name")).orElse("main");
        Type returnType;
        try {
            returnType = types.getExprType(node, methodName);
        } catch (RuntimeException e) {
            // If we can't determine the type, use void as fallback for imported methods
            returnType = new Type("void", false);
        }
        String ollirReturnType = ollirTypes.toOllirType(returnType);

        // Build method invocation code
        if (table.getImports().stream().anyMatch(imp -> imp.endsWith("." + callerName) || imp.equals(callerName))) {
            // Static invocation for imported classes
            String args = String.join(", ", argCodes);
            String code = "invokestatic(" + callerResult.getCode() + ", \"" + method + "\"" +
                    (args.isEmpty() ? "" : ", " + args) + ")" + ollirReturnType;
            return new OllirExprResult(code, computation);
        } else if (callerName.equals("this") || callerName.equals(table.getClassName())) {
            // Virtual invocation for methods in the current class
            String args = String.join(", ", argCodes);
            String code = "invokevirtual(" + callerResult.getCode() + ", \"" + method + "\"" +
                    (args.isEmpty() ? "" : ", " + args) + ")" + ollirReturnType;
            return new OllirExprResult(code, computation);
        } else {
            // Regular virtual invocation for other objects
            String args = String.join(", ", argCodes);
            String code = "invokevirtual(" + callerResult.getCode() + ", \"" + method + "\"" +
                    (args.isEmpty() ? "" : ", " + args) + ")" + ollirReturnType;
            return new OllirExprResult(code, computation);
        }
    }

    private OllirExprResult visitPostfixExpr(JmmNode node, Void unused) {
        // Not implemented for brevity
        return OllirExprResult.EMPTY;
    }

    private OllirExprResult visitArrayLiteralExpr(JmmNode node, Void unused) {
        // Not implemented for brevity
        return OllirExprResult.EMPTY;
    }

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        if (node.getNumChildren() == 0) return OllirExprResult.EMPTY;
        return visit(node.getChild(0));
    }
}