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
        addVisit(WITH_ELSE_STMT, this::visitWithElseStmt);
        addVisit(NO_ELSE_STMT, this::visitNoElseStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        addVisit(FOR_STMT, this::visitForStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        if (node.getNumChildren() < 2) {
            return "// Incomplete assignment statement\n";
        }

        var lhs = node.getChild(0);
        var rhs = exprVisitor.visit(node.getChild(1));
        code.append(rhs.getComputation());

        Type lhsType = types.getExprType(lhs);
        String ollirType = ollirTypes.toOllirType(lhsType);
        String lhsName = lhs.get("name");

        // Get method name to check local vars
        String methodName = node.getAncestor(METHOD_DECL.getNodeName()).map(m -> m.get("name")).orElse(null);

        boolean isLocal = methodName != null &&
                (table.getParameters(methodName).stream().anyMatch(s -> s.getName().equals(lhsName)) ||
                        table.getLocalVariables(methodName).stream().anyMatch(s -> s.getName().equals(lhsName)));

        if (!isLocal && table.getFields().stream().anyMatch(f -> f.getName().equals(lhsName))) {
            // FIELD ASSIGNMENT => putfield
            String className = table.getClassName();
            code.append("putfield(this.").append(className).append(", ")
                    .append(lhsName).append(ollirType).append(", ")
                    .append(rhs.getCode()).append(")")
                    .append(ollirType).append(END_STMT);
        } else {
            // Local or param => normal assignment
            code.append(lhsName).append(ollirType).append(SPACE)
                    .append(ASSIGN).append(ollirType).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);
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
        StringBuilder code = new StringBuilder();

        // Add access modifiers
        boolean isPublic = node.getBoolean("isPublic", false);
        if (isPublic) {
            code.append(".method public ");
        } else {
            code.append(".method ");
        }

        // Get method name and check if it's main
        String methodName = node.get("name");
        boolean isStatic = methodName.equals("main");

        if (isStatic) {
            code.append("static ");
        }

        // Add method name
        code.append(methodName).append("(");

        // Add parameters
        var params = node.getChildren().stream()
                .filter(child -> PARAM.check(child))
                .map(child -> visit(child))
                .collect(Collectors.joining(", "));
        code.append(params).append(")");

        // Add return type - Check if there are children before accessing
        Type returnType;
        if (node.getNumChildren() > 0 && (
                TYPE.check(node.getChild(0)) ||
                        VAR_ARRAY.check(node.getChild(0)) ||
                        VAR_ARGS.check(node.getChild(0)))) {
            var returnOllirType = ollirTypes.toOllirType(node.getChild(0));
            code.append(returnOllirType);
            // Save return type for later
            returnType = TypeUtils.convertType(node.getChild(0));
        } else {
            code.append(".V"); // void return type
            returnType = new Type("void", false);
        }

        // Start method body
        code.append(L_BRACKET);

        // Add local variable initializations
        var locals = table.getLocalVariables(methodName);
        if (locals != null) {  // Add null check
            for (var localVar : locals) {
                var type = localVar.getType();
                var localName = localVar.getName();
                var ollirType = ollirTypes.toOllirType(type);
                code.append(localName).append(ollirType)
                        .append(SPACE).append(ASSIGN).append(ollirType).append(SPACE);

                // Initialize with default value based on type
                if (type.getName().equals("int")) {
                    code.append("0").append(ollirType);
                } else if (type.getName().equals("boolean")) {
                    code.append("0").append(ollirType);
                } else {
                    code.append("0").append(ollirType); // Default for objects
                }
                code.append(END_STMT);
            }
        }

        // Process statements
        for (var child : node.getChildren()) {
            if (child.getKind().equals("Stmt")) {
                String stmt = visit(child);
                code.append(stmt);
            }
        }

        // Process return expression if present (binary expressions, method calls, etc.)
        for (var child : node.getChildren()) {
            if (child.getKind().equals("BinaryExpr") ||
                    child.getKind().equals("MethodCallExpr") ||
                    child.getKind().equals("ArrayAccessExpr")) {

                // Use expression visitor to get the result
                OllirExprResult exprResult = exprVisitor.visit(child);

                // Add the computation code
                code.append(exprResult.getComputation());

                // Add the return statement
                if (!isStatic) { // If not main method
                    code.append("ret").append(ollirTypes.toOllirType(types.getExprType(child, methodName)))
                            .append(SPACE)
                            .append(exprResult.getCode())
                            .append(END_STMT);
                }
            }
        }

        // If no explicit return was added and it's not void, add a default return
        if (!isStatic && !code.toString().contains("ret") && !returnType.getName().equals("void")) {
            String returnOllirType = ollirTypes.toOllirType(returnType);
            code.append("ret").append(returnOllirType).append(SPACE).append("0").append(returnOllirType).append(END_STMT);
        }

        // End method
        code.append(R_BRACKET);

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
        StringBuilder code = new StringBuilder();

        // Get the condition expression
        var condExpr = exprVisitor.visit(node.getChild(0));
        code.append(condExpr.getComputation());

        // Create a unique label for the else branch and the end
        String elseLabel = ollirTypes.nextTemp("else");
        String endLabel = ollirTypes.nextTemp("endif");

        // If condition is false, jump to else
        code.append("if (").append(condExpr.getCode()).append(") goto ").append(elseLabel).append(END_STMT);

        // Then branch - visit the 'then' statement (child 1)
        code.append(visit(node.getChild(1)));

        // Jump to end after executing 'then'
        code.append("goto ").append(endLabel).append(END_STMT);

        // Else label and branch - visit the 'else' statement (child 2)
        code.append(elseLabel).append(":").append(NL);
        code.append(visit(node.getChild(2)));

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitNoElseStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Get the condition expression
        var condExpr = exprVisitor.visit(node.getChild(0));
        code.append(condExpr.getComputation());

        // Create a unique label for the end
        String endLabel = ollirTypes.nextTemp("endif");

        // If condition is false, jump to end
        code.append("if (!").append(condExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // Visit the 'then' statement (child 1)
        code.append(visit(node.getChild(1)));

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        // Generate unique labels for the loop
        String loopLabel = "while" + System.identityHashCode(node);
        String endLabel = "end" + loopLabel;

        // Visit the condition expression
        var condExpr = exprVisitor.visit(node.getChild(0)); // Condition is the first child
        StringBuilder code = new StringBuilder();

        // Start of loop
        code.append(loopLabel).append(":\n");

        // Add computation for the condition
        code.append(condExpr.getComputation());

        // Evaluate the condition and add a conditional branch
        code.append("if (!").append(condExpr.getCode()).append(") goto ").append(endLabel).append(";\n");

        // Visit and process the loop body (second child)
        String bodyCode = visit(node.getChild(1));
        code.append(bodyCode);

        // Jump back to the start of the loop
        code.append("goto ").append(loopLabel).append(";\n");

        // End of loop
        code.append(endLabel).append(":\n");

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

    private String visitForStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Create unique labels for loop
        String loopLabel = ollirTypes.nextTemp("forloop");
        String endLabel = ollirTypes.nextTemp("endforloop");

        // Initialize loop variable (first child)
        code.append(visit(node.getChild(0)));

        // Loop label
        code.append(loopLabel).append(":").append(NL);

        // Get the condition expression (second child)
        var condExpr = exprVisitor.visit(node.getChild(1));
        code.append(condExpr.getComputation());

        // If condition is false, exit loop
        code.append("if (!").append(condExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // Loop body - visit the body statement (fourth child)
        code.append(visit(node.getChild(3)));

        // Update expression (third child)
        var updateExpr = exprVisitor.visit(node.getChild(2));
        code.append(updateExpr.getComputation());

        // Jump back to start of loop
        code.append("goto ").append(loopLabel).append(END_STMT);

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        // Get the expression and visit it
        OllirExprResult exprResult = exprVisitor.visit(node.getChild(0));

        // Build the complete statement
        StringBuilder code = new StringBuilder();

        // Include any computation needed before the expression
        code.append(exprResult.getComputation());

        // For expressions like method calls, we need to include the actual call
        // expression as a statement
        if (!exprResult.getComputation().contains(exprResult.getCode())) {
            code.append(exprResult.getCode()).append(";\n");
        }

        return code.toString();
    }
}
