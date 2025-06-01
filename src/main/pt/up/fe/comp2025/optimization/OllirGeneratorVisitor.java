package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(IF_ELSE_STMT, this::visitWithElseStmt);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();


        if (node.getNumChildren() != 2) {
            System.out.println("[visitAssignStmt] Unexpected child count in AssignStmt: " + node.getNumChildren());
            return "// Unexpected assign format\n";
        }

        JmmNode lhs = node.getChild(0);
        JmmNode rhsNode = node.getChild(1);

        var rhsResult = exprVisitor.visit(rhsNode);
        code.append(rhsResult.getComputation());

        String methodName = node.getAncestor("MethodDecl").map(m -> m.get("name")).orElse("main");
        if ("args".equals(methodName)) methodName = "main";

        // Handle array assignments like a[i] = ...
        if (lhs.getKind().equals("ArrayAccessExpr")) {
            var arrayExpr = exprVisitor.visit(lhs.getChild(0)); // array name (e.g., a)
            var indexExpr = exprVisitor.visit(lhs.getChild(1)); // index (e.g., i)

            code.append(arrayExpr.getComputation());
            code.append(indexExpr.getComputation());

            Type elemType = types.getExprType(lhs, methodName);
            String ollirElemType = ollirTypes.toOllirType(elemType);

            code.append(arrayExpr.getCode()).append("[")
                    .append(indexExpr.getCode()).append("]")
                    .append(ollirElemType).append(" ")
                    .append(ASSIGN).append(ollirElemType).append(" ")
                    .append(rhsResult.getCode()).append(END_STMT);

            return code.toString();
        }

        // Regular variable assignment
        // Handle special cases for left-hand side expressions
        JmmNode effectiveLhs = lhs;
        
        // If the lhs is a ParenthesizedExpr, unwrap it to get the actual variable node
        if (lhs.getKind().equals("ParenthesizedExpr")) {
            effectiveLhs = lhs.getChild(0);
        }
        
        // Only try to access 'value' attribute if the node type is expected to have it
        // Using final for lhsName since it's used in lambda expressions
        final String lhsName;
        if (effectiveLhs.getKind().equals("VarRefExpr") || 
            effectiveLhs.getKind().equals("PostfixExpr") || 
            effectiveLhs.hasAttribute("value")) {
            lhsName = effectiveLhs.get("value");
        } else {
            lhsName = "UNKNOWN_ID";
        }
        
        Type lhsType = types.getExprType(lhs, methodName);
        String ollirType = ollirTypes.toOllirType(lhsType);

        boolean isLocalOrParam =
                table.getLocalVariables(methodName).stream().anyMatch(s -> s.getName().equals(lhsName)) ||
                        table.getParameters(methodName).stream().anyMatch(s -> s.getName().equals(lhsName));

        boolean isField = table.getFields().stream().anyMatch(f -> f.getName().equals(lhsName));

        if (!isLocalOrParam && isField) {
            code.append("putfield(this.").append(table.getClassName())
                    .append(", ").append(lhsName).append(ollirType)
                    .append(", ").append(rhsResult.getCode()).append(")")
                    .append(ollirType).append(END_STMT);
        } else {
            code.append(lhsName).append(ollirType).append(" ")
                    .append(ASSIGN).append(ollirType).append(" ")
                    .append(rhsResult.getCode()).append(END_STMT);
        }

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Get the method name from the ancestors
        JmmNode methodNode = node;
        while (methodNode != null && !methodNode.getKind().equals(METHOD_DECL.getNodeName())) {
            methodNode = methodNode.getParent();
        }

        if (methodNode == null) {
            throw new RuntimeException("Return statement outside method context");
        }

        String methodName = methodNode.get("name");

        // Get the return type of the method
        Type retType = table.getReturnType(methodName);

        // Process the expression if it exists
        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;

        // Add computation code
        code.append(expr.getComputation());

        // Add the return instruction with the correct type
        code.append("ret");
        code.append(ollirTypes.toOllirType(retType));
        code.append(SPACE);

        // Add the expression code if present
        if (node.getNumChildren() > 0) {
            code.append(expr.getCode());
        }

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        // Add access modifiers
        boolean isPublic = node.getBoolean("isPublic", false);
        if (isPublic) code.append("public ");
        boolean isStatic = node.getBoolean("isStatic", false);
        if (isStatic) code.append("static ");

        // Method name
        String name = node.get("name");
        if (name.equals("args")) name = "main";
        code.append(name).append("(");

        // Parameters
        var paramNodes = node.getChildren(PARAM);
        if (!paramNodes.isEmpty()) {
            var paramCodes = paramNodes.stream().map(this::visit).collect(Collectors.joining(", "));
            code.append(paramCodes);
        }

        code.append(")");

        // Return type
        Type returnType = name.equals("main") ? new Type("void", false) : table.getReturnType(name);
        if (returnType == null) returnType = new Type("void", false);
        code.append(ollirTypes.toOllirType(returnType));

        code.append(" {\n");

        // Local variables
        for (var varDecl : node.getChildren(VAR_DECL)) {
            JmmNode typeNode = varDecl.getChild(0);
            String varName = varDecl.get("name");
            Type varType = types.convertType(typeNode);
            String ollirType = ollirTypes.toOllirType(varType);
            code.append("    ").append(varName).append(ollirType).append(" :=").append(ollirType)
                    .append(" 0").append(ollirType).append(";\n");
        }

        // Process statements and expressions
        for (var child : node.getChildren()) {
            String kind = child.getKind();

            if (kind.endsWith("Stmt") || kind.contains("Stmt")) {
                String stmtCode = visit(child);
                if (!stmtCode.isEmpty()) {
                    code.append("    ").append(stmtCode);
                }
            } else if (kind.equals(VAR_REF_EXPR.getNodeName()) || kind.endsWith("Expr")) {
                var exprResult = exprVisitor.visit(child);

                if (!exprResult.getComputation().isBlank()) {
                    code.append("    ").append(exprResult.getComputation());
                }

                // Emit a return using the evaluated expression
                if (!exprResult.getCode().isBlank()) {
                    String tempVar = ollirTypes.nextTemp("retVal");
                    String retType = ollirTypes.toOllirType(returnType);

                    // Assign result to temp
                    code.append("    ").append(tempVar).append(retType)
                            .append(" :=").append(retType)
                            .append(" ").append(exprResult.getCode()).append(";\n");

                    // Return the temp
                    code.append("    ret").append(retType).append(" ").append(tempVar).append(retType).append(";\n");
                }
            }
        }

        // Return fallback if no return statement exists
        boolean hasReturn = node.getChildren().stream().anyMatch(child -> child.getKind().equals("ReturnStmt"));
        if (!hasReturn) {
            if (returnType.getName().equals("void")) {
                code.append("    ret.V;\n");
            } else {
                String defaultVal = returnType.getName().equals("boolean") || returnType.getName().equals("int")
                        ? "0" + ollirTypes.toOllirType(returnType)
                        : "null" + ollirTypes.toOllirType(returnType);
                code.append("    ret").append(ollirTypes.toOllirType(returnType))
                        .append(" ").append(defaultVal).append(";\n");
            }
        }

        code.append("}\n\n");
        return code.toString();
    }




    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        // Add extends clause if there is a superclass
        String superClassName = table.getSuper();
        if (superClassName != null && !superClassName.isEmpty()) {
            code.append(" extends ").append(superClassName);
        }

        code.append(SPACE).append(L_BRACKET).append(NL).append(NL);

        for (var field : table.getFields()) {
            Type fieldType = field.getType();
            String ollirType = ollirTypes.toOllirType(fieldType);
            String fieldName = field.getName();

            code.append(".field public ").append(fieldName).append(ollirType).append(";").append(NL);
        }

        code.append(NL);
        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);
        return code.toString();
    }


    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        // Generate import statements at the beginning of the file
        for (String importStr : table.getImports()) {
            code.append("import ").append(importStr).append(";").append(NL);
        }

        // Add an extra line after imports if any
        if (!table.getImports().isEmpty()) {
            code.append(NL);
        }

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }

    private String visitWithElseStmt(JmmNode node, Void unused) {

        /*
        System.out.println("[DEBUG] visitWithElseStmt:");
        System.out.println("Condition: " + node.getChild(0).toTree());
        System.out.println("Then block: " + node.getChild(1).toTree());
        System.out.println("Else block: " + node.getChild(2).toTree());
         */

        StringBuilder code = new StringBuilder();

        // Get the condition expression
        var condExpr = exprVisitor.visit(node.getChild(0));
        code.append(condExpr.getComputation());

        // Create unique labels for the if/else structure using the new method
        String elseLabel = ollirTypes.nextControlFlowLabel("else");
        String endLabel = ollirTypes.nextControlFlowLabel("endif");
        String thenLabel = ollirTypes.nextControlFlowLabel("then");

        // If condition is false, jump to else
        code.append("if (").append(condExpr.getCode()).append(") goto ").append(thenLabel).append(END_STMT);
        code.append(elseLabel).append(":").append(NL);
        code.append(visit(node.getChild(2).getChild(0)));
        code.append("goto ").append(endLabel).append(END_STMT);
        code.append(thenLabel).append(":").append(NL);
        code.append(visit(node.getChild(1).getChild(0)));
        code.append(endLabel).append(":").append(NL);


        //System.out.println("[DEBUG] OLLIR if-else emitted:\n" + code);


        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Use the nextControlFlowLabel method to create unique labels
        String loopLabel = ollirTypes.nextControlFlowLabel("loop");
        String endLabel = ollirTypes.nextControlFlowLabel("endloop");

        code.append(loopLabel).append(":").append(NL);

        var condExpr = exprVisitor.visit(node.getChild(0));
        code.append(condExpr.getComputation());

        // Fixed OLLIR syntax for the if statement
        code.append("if (!.bool ").append(condExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        code.append(visit(node.getChild(1)));
        code.append("goto ").append(loopLabel).append(END_STMT);
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }




    private String visitBlockStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Visit all statements in the block
        for (JmmNode stmt : node.getChildren()) {
            code.append(visit(stmt));
        }

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        // Process the expression
        var expr = exprVisitor.visit(node.getChild(0));

        // Return the computation which should include the method call
        return expr.getComputation();
    }
}
