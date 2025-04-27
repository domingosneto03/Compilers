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
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(IMPORT_STMT, this::visitImport);
        addVisit(WITH_ELSE_STMT, this::visitWithElseStmt);
        addVisit(NO_ELSE_STMT,   this::visitNoElseStmt);
        addVisit(WHILE_STMT,     this::visitWhileStmt);
        addVisit(OTHER_STMT,     this::visitOtherStmt);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        if (node.getNumChildren() < 2) {
            // Handle the case with insufficient children
            System.err.println("Warning: AssignStmt has fewer than 2 children");
            return "// Malformed assignment statement\n";
        }

        var rhs = exprVisitor.visit(node.getChild(1));

        // code to compute the children
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        var left = node.getChild(0);
        Type thisType = types.getExprType(left);
        String typeString = ollirTypes.toOllirType(thisType);
        var varCode = left.get("name") + typeString;

        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Get the current method's name from the ancestor nodes
        String methodName = null;
        JmmNode current = node;
        while (current != null && methodName == null) {
            if (current.getKind().equals(METHOD_DECL.getNodeName())) {
                methodName = current.get("name");
            }
            current = current.getParent();
        }

        // Get the method's return type from the symbol table
        Type retType = null;
        if (methodName != null) {
            retType = table.getReturnType(methodName);
        }

        // If return type couldn't be determined, fall back to int as default
        if (retType == null) {
            retType = types.newIntType();
        }

        String retTypeCode = ollirTypes.toOllirType(retType);
        if (retTypeCode.startsWith(".")) {
            retTypeCode = retTypeCode.substring(1);
        }

        // Process the return expression if it exists
        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;

        code.append(expr.getComputation());
        code.append("ret.");
        code.append(retTypeCode);
        code.append(SPACE);

        if (node.getNumChildren() > 0) {
            code.append(expr.getCode());
        } else {
            // If no expression is provided (void return), don't append any code
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
        String methodName = node.get("name");
        StringBuilder code = new StringBuilder(".method ");

        if (node.getBoolean("isPublic", false)) {
            code.append("public ");
        }
        if (node.hasAttribute("isstatic")) {
            code.append("static ");
        }

        boolean isMain = methodName.equals("main");
        Type rawReturn = isMain
                ? types.newVoidType()
                : table.getReturnType(methodName) != null
                ? table.getReturnType(methodName)
                : types.newIntType();
        String retTypeCode = ollirTypes.toOllirType(rawReturn);

        // Format: .method [public][static] methodName(params).returnType
        code.append(methodName);

        var params = node.getChildren(PARAM);
        StringBuilder paramsCode = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) paramsCode.append(", ");
            paramsCode.append(visit(params.get(i)));
        }
        code.append("(").append(paramsCode).append(")");

        // Add return type after parameters
        code.append(retTypeCode).append(L_BRACKET).append(NL);

        for (var child : node.getChildren()) {
            String kind = child.getKind();
            if (kind.equals(VAR_DECL.getNodeName()) ||
                    kind.startsWith("Stmt") ||
                    kind.equals(WITH_ELSE_STMT.getNodeName()) ||
                    kind.equals(NO_ELSE_STMT.getNodeName()) ||
                    kind.equals(WHILE_STMT.getNodeName()) ||
                    kind.equals(OTHER_STMT.getNodeName())) {
                code.append("    ").append(visit(child));
            }
        }

        // Only enforce return for non-void methods
        if (!rawReturn.getName().equals("void")) {
            // Check if there's a return statement, otherwise generate a default return
            JmmNode ret = node.getChildren().stream()
                    .filter(c -> c.getKind().equals(RETURN_STMT.getNodeName()))
                    .findFirst()
                    .orElse(null);

            if (ret != null) {
                code.append("    ").append(visit(ret));
            } else {
                // Generate a default return statement with a default value
                if (rawReturn.isArray()) {
                    code.append("    ret").append(retTypeCode).append(" null").append(retTypeCode).append(END_STMT);
                } else if (rawReturn.getName().equals("int")) {
                    code.append("    ret").append(retTypeCode).append(" 0").append(retTypeCode).append(END_STMT);
                } else if (rawReturn.getName().equals("boolean")) {
                    code.append("    ret").append(retTypeCode).append(" 0").append(retTypeCode).append(END_STMT);
                } else {
                    // For objects
                    code.append("    ret").append(retTypeCode).append(" null").append(retTypeCode).append(END_STMT);
                }
            }
        }

        code.append(R_BRACKET).append(NL);
        return code.toString();
    }




    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());
        
        code.append(L_BRACKET);
        code.append(NL);
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

    private String visitVarDecl(JmmNode node, Void unused) {
        // Get the variable name
        String varName = node.get("name");

        // Get the type from the first child
        JmmNode typeNode = node.getChild(0);
        Type type = TypeUtils.convertType(typeNode);
        String ollirType = ollirTypes.toOllirType(type);

        // In OLLIR, local variables need to be initialized with default values
        StringBuilder code = new StringBuilder();

        // Format: varName.type :=.type defaultValue.type;
        code.append(varName).append(ollirType)
                .append(SPACE).append(ASSIGN).append(ollirType).append(SPACE);

        // Default initialization value (0 for primitive types and null references)
        code.append("0").append(ollirType);

        code.append(END_STMT);

        return code.toString();
    }


    private String visitImport(JmmNode node, Void unused) {
        // Names in imports are stored as individual attributes
        StringBuilder importPath = new StringBuilder();
        int index = 0;

        // Collect all parts of the import name
        while (node.hasAttribute("name" + index)) {
            if (index > 0) {
                importPath.append(".");
            }
            importPath.append(node.get("name" + index));
            index++;
        }

        // Format as OLLIR import statement
        return "import " + importPath + ";" + NL;
    }

    private String visitWithElseStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // First child is the condition expression
        var conditionExpr = exprVisitor.visit(node.getChild(0));
        code.append(conditionExpr.getComputation());

        // Generate a unique label for the else branch and end branch
        String elseLabel = ollirTypes.nextLabel();
        String endLabel = ollirTypes.nextLabel();

        // Evaluate condition and jump to else if false
        code.append("if (").append(conditionExpr.getCode()).append(") goto ").append(elseLabel).append("_else").append(END_STMT);

        // Then branch (second child)
        code.append(visit(node.getChild(1)));

        // Jump to end after then branch
        code.append("goto ").append(endLabel).append(END_STMT);

        // Else label
        code.append(elseLabel).append("_else:").append(NL);

        // Else branch (third child)
        code.append(visit(node.getChild(2)));

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitNoElseStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // First child is the condition expression
        var conditionExpr = exprVisitor.visit(node.getChild(0));
        code.append(conditionExpr.getComputation());

        // Generate a unique label for the end of the if statement
        String endLabel = ollirTypes.nextLabel();

        // Evaluate condition and jump to end if false
        code.append("if (").append(conditionExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // Then branch (second child)
        code.append(visit(node.getChild(1)));

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Generate labels for loop start and end
        String startLabel = ollirTypes.nextLabel();
        String endLabel = ollirTypes.nextLabel();

        // Place the start label
        code.append(startLabel).append(":").append(NL);

        // First child is the condition expression
        var conditionExpr = exprVisitor.visit(node.getChild(0));
        code.append(conditionExpr.getComputation());

        // Evaluate condition and jump to end if false
        code.append("if (").append(conditionExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // Loop body (second child)
        code.append(visit(node.getChild(1)));

        // Jump back to start
        code.append("goto ").append(startLabel).append(END_STMT);

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitOtherStmt(JmmNode node, Void unused) {
        // This method handles block statements and expression statements
        // The actual block statement is the first child of the OTHER_STMT node
        JmmNode actualStmt = node.getChild(0);

        // If it's a block statement, we need to visit all its children statements
        if (actualStmt.getKind().equals("BlockStmt")) {
            StringBuilder code = new StringBuilder();
            for (JmmNode childStmt : actualStmt.getChildren()) {
                code.append(visit(childStmt));
            }
            return code.toString();
        }
        // For expression statements, we delegate to the expression visitor
        else if (actualStmt.getKind().equals("ExprStmt")) {
            JmmNode expr = actualStmt.getChild(0);
            var exprResult = exprVisitor.visit(expr);
            return exprResult.getComputation();
        }
        // For any other kind of statement, visit it directly
        else {
            return visit(actualStmt);
        }
    }
}
