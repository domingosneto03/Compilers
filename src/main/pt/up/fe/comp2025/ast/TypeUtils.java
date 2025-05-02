package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;

public class TypeUtils {

    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    /**
     * Converts a type node (built according to the grammar) into a {@link Type}.
     * It distinguishes between plain types, array types (VarArray) and varargs (VarArgs).
     *
     * @param typeNode the AST node representing the type
     * @return the corresponding Type
     */
    public static Type convertType(JmmNode typeNode) {
        String typeName = typeNode.get("value");
        boolean isArray = false;
        String kind = typeNode.getKind();
        if ("VarArray".equals(kind) || "VarArgs".equals(kind)) {
            isArray = true;
        }
        return new Type(typeName, isArray);
    }

    /**
     * Gets the {@link Type} of an arbitrary expression in the context of a given method.
     *
     * @param expr the AST node representing the expression
     * @param currentMethod the current method signature in which the expression is evaluated
     * @return the inferred type of the expression
     */
    public Type getExprType(JmmNode expr, String currentMethod) {
        String kind = expr.getKind();
        switch (kind) {
            case "IntegerLiteral":
                return new Type("int", false);
            case "BooleanTrue":
            case "BooleanFalse":
                return new Type("boolean", false);
            case "VarRefExpr": {
                String id = expr.get("value");
                // Check local variables.
                List<Symbol> locals = table.getLocalVariables(currentMethod);
                for (Symbol symbol : locals) {
                    if (symbol.getName().equals(id)) {
                        return symbol.getType();
                    }
                }
                // Check method parameters.
                List<Symbol> params = table.getParameters(currentMethod);
                for (Symbol symbol : params) {
                    if (symbol.getName().equals(id)) {
                        return symbol.getType();
                    }
                }
                // Check class fields.
                for (Symbol symbol : table.getFields()) {
                    if (symbol.getName().equals(id)) {
                        return symbol.getType();
                    }
                }
                throw new RuntimeException("Undefined identifier: " + id);
            }
            case "ThisExpr":
                return new Type(table.getClassName(), false);
            case "ParenthesizedExpr":
                return getExprType(expr.getChild(0), currentMethod);
            case "UnaryExpr": {
                // Assume the only supported unary operator is '!'
                Type operandType = getExprType(expr.getChild(0), currentMethod);
                if (!"boolean".equals(operandType.getName())) {
                    throw new RuntimeException("Unary operator '!' applied to non-boolean type");
                }
                return new Type("boolean", false);
            }
            case "NewIntArrayExpr":
                return new Type("int", true);
            case "NewObjectExpr": {
                String objType = expr.get("value");
                return new Type(objType, false);
            }
            case "PostfixExpr": {
                String id = expr.get("value");
                // Similar lookup as for VarRefExpr.
                List<Symbol> locals = table.getLocalVariables(currentMethod);
                for (Symbol symbol : locals) {
                    if (symbol.getName().equals(id)) {
                        return symbol.getType();
                    }
                }
                List<Symbol> params = table.getParameters(currentMethod);
                for (Symbol symbol : params) {
                    if (symbol.getName().equals(id)) {
                        return symbol.getType();
                    }
                }
                for (Symbol symbol : table.getFields()) {
                    if (symbol.getName().equals(id)) {
                        return symbol.getType();
                    }
                }
                throw new RuntimeException("Undefined identifier in postfix expression: " + id);
            }
            case "ArrayAccessExpr": {
                // The first child is the array expression.
                Type arrayType = getExprType(expr.getChild(0), currentMethod);
                if (!arrayType.isArray()) {
                    throw new RuntimeException("Array access on non-array type: " + arrayType.getName());
                }
                // Return the element type.
                return new Type(arrayType.getName(), false);
            }
            case "ArrayLengthExpr":
                return new Type("int", false);
            case "MethodCallExpr": {
                String methodName = expr.get("method");
                // Evaluate the caller type.
                Type callerType = getExprType(expr.getChild(0), currentMethod);
                // In this implementation, we only have information for methods in the current class.
                if (!callerType.getName().equals(table.getClassName())) {
                    throw new RuntimeException("Method call on unknown class: " + callerType.getName());
                }
                Type returnType = table.getReturnType(methodName);
                if (returnType == null) {
                    throw new RuntimeException("Undefined method call: " + methodName);
                }
                return returnType;
            }
            case "BinaryExpr": {
                // All binary operations are unified under BinaryExpr.
                String op = expr.get("op");
                if (op.equals("*") || op.equals("/") || op.equals("+") || op.equals("-")) {
                    return new Type("int", false);
                } else if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")
                        || op.equals("==") || op.equals("!=")) {
                    return new Type("boolean", false);
                } else if (op.equals("&&") || op.equals("||")) {
                    return new Type("boolean", false);
                } else if (op.equals("+=") || op.equals("-=") || op.equals("*=") || op.equals("/=")) {
                    // For assignment operators, return the type of the left-hand side.
                    return getExprType(expr.getChild(0), currentMethod);
                } else {
                    throw new RuntimeException("Unsupported operator in BinaryExpr: " + op);
                }
            }
            case "ArrayLiteralExpr": {
                if (expr.getChildren().isEmpty()) {
                    return new Type("int", true); // empty literal defaults to int[]
                }

                Type firstType = getExprType(expr.getChild(0), currentMethod);

                for (int i = 1; i < expr.getNumChildren(); i++) {
                    Type currentType = getExprType(expr.getChild(i), currentMethod);
                    if (!firstType.getName().equals(currentType.getName()) || firstType.isArray() != currentType.isArray()) {
                        throw new RuntimeException("Inconsistent types in array initializer: " + firstType + " vs " + currentType);
                    }
                }

                return new Type(firstType.getName(), true);
            }

            default:
                throw new RuntimeException("Unsupported expression type: " + kind);
        }
    }

    /**
     * Convenience method that assumes a 'main' method context.
     *
     * @param expr the AST node representing the expression
     * @return the inferred type of the expression
     */
    public Type getExprType(JmmNode expr) {
        return getExprType(expr, "main");
    }

    /**
     * Infer the type of an expression node, usando o mesmo algoritmo de getExprType.
     */
    public static Type inferExprType(JmmNode expr, SymbolTable table, String currentMethod) {
        return new TypeUtils(table).getExprType(expr, currentMethod);
    }

    /**
     * Verifica compatibilidade entre t1 (esperado) e t2 (atual):
     * - mesmos nome e array-flag, ou
     * - se forem objetos não-array, verifica herança usando o symbol table.
     */
    public static boolean compatibleWith(Type expected, Type actual, SymbolTable table) {
        if (expected.equals(actual)) {
            return true;
        }
        // se forem objetos (não primitivos nem array)
        if (!expected.isArray() && !actual.isArray()
                && !isPrimitive(expected.getName())) {
            // percorre a chain de extends
            String sup = ((JmmSymbolTable) table).getSuper();
            String cur = actual.getName();
            while (sup != null && !sup.isEmpty()) {
                if (sup.equals(expected.getName())) return true;
                // next up
                cur = sup;
                sup = ((JmmSymbolTable) table).getSuper();
            }
        }
        return false;
    }

    private static boolean isPrimitive(String name) {
        return name.equals("int") || name.equals("boolean")
                || name.equals("double") || name.equals("float")
                || name.equals("String");
    }


}