package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;

import java.util.Collections;

public class ConstantFoldingVisitor extends PostorderJmmVisitor<String, Boolean> {

    private final SymbolTable table;
    private boolean changed;

    public ConstantFoldingVisitor(SymbolTable table) {
        this.table = table;
        this.changed = false;
        setDefaultVisit(this::defaultVisit);
        addVisit("BinaryExpr", this::visitBinaryExpr);
        addVisit("UnaryExpr", this::visitUnaryExpr);
    }

    public boolean didChange() {
        return changed;
    }

    private Boolean visitBinaryExpr(JmmNode node, String dummy) {
        changed |= visit(node.getChild(0)) || visit(node.getChild(1));

        var left = node.getChild(0);
        var right = node.getChild(1);
        var op = node.get("op");

        System.out.println("Visiting BinaryExpr: " + op);

        // Handle integer arithmetic operations
        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
            return foldIntegerBinaryExpr(node, left, right, op);
        }

        // Handle boolean operations
        if ((left.getKind().equals("BooleanTrue") || left.getKind().equals("BooleanFalse")) &&
                (right.getKind().equals("BooleanTrue") || right.getKind().equals("BooleanFalse"))) {
            return foldBooleanBinaryExpr(node, left, right, op);
        }

        // Handle comparisons
        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral") &&
                (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=") || op.equals("==") || op.equals("!="))) {
            return foldComparisonExpr(node, left, right, op);
        }

        return false;
    }

    private Boolean visitUnaryExpr(JmmNode node, String dummy) {
        // Visit child first
        changed |= visit(node.getChild(0));

        var operand = node.getChild(0);
        var op = node.get("op");

        System.out.println("Visiting UnaryExpr: " + op);

        // Handle boolean negation
        if (op.equals("!") && (operand.getKind().equals("BooleanTrue") || operand.getKind().equals("BooleanFalse"))) {
            boolean value = operand.getKind().equals("BooleanTrue");
            boolean result = !value;

            System.out.printf("Folding: !%s = %s%n", value, result);

            var replacement = new JmmNodeImpl(Collections.singletonList(result ? "BooleanTrue" : "BooleanFalse"));
            replacement.put("value", result ? "true" : "false");

            node.replace(replacement);
            changed = true;
            return true;
        }

        return false;
    }

    private boolean foldIntegerBinaryExpr(JmmNode node, JmmNode left, JmmNode right, String op) {
        int lhs = Integer.parseInt(left.get("value"));
        int rhs = Integer.parseInt(right.get("value"));

        Integer result = switch (op) {
            case "+" -> lhs + rhs;
            case "-" -> lhs - rhs;
            case "*" -> lhs * rhs;
            case "/" -> (rhs != 0) ? lhs / rhs : null;
            default -> null;
        };

        if (result == null) return false;

        System.out.printf("Folding: %d %s %d = %d%n", lhs, op, rhs, result);

        var replacement = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
        replacement.put("value", Integer.toString(result));

        node.replace(replacement);
        changed = true;
        return true;
    }

    private boolean foldBooleanBinaryExpr(JmmNode node, JmmNode left, JmmNode right, String op) {
        boolean lhs = left.getKind().equals("BooleanTrue");
        boolean rhs = right.getKind().equals("BooleanTrue");

        Boolean result = switch (op) {
            case "&&" -> lhs && rhs;
            case "||" -> lhs || rhs;
            case "==" -> lhs == rhs;
            case "!=" -> lhs != rhs;
            default -> null;
        };

        if (result == null) return false;

        System.out.printf("Folding: %s %s %s = %s%n", lhs, op, rhs, result);

        var replacement = new JmmNodeImpl(Collections.singletonList(result ? "BooleanTrue" : "BooleanFalse"));
        replacement.put("value", result ? "true" : "false");

        node.replace(replacement);
        changed = true;
        return true;
    }

    private boolean foldComparisonExpr(JmmNode node, JmmNode left, JmmNode right, String op) {
        int lhs = Integer.parseInt(left.get("value"));
        int rhs = Integer.parseInt(right.get("value"));

        Boolean result = switch (op) {
            case "<" -> lhs < rhs;
            case ">" -> lhs > rhs;
            case "<=" -> lhs <= rhs;
            case ">=" -> lhs >= rhs;
            case "==" -> lhs == rhs;
            case "!=" -> lhs != rhs;
            default -> null;
        };

        if (result == null) return false;

        System.out.printf("Folding: %d %s %d = %s%n", lhs, op, rhs, result);

        var replacement = new JmmNodeImpl(Collections.singletonList(result ? "BooleanTrue" : "BooleanFalse"));
        replacement.put("value", result ? "true" : "false");

        node.replace(replacement);
        changed = true;
        return true;
    }

    private Boolean defaultVisit(JmmNode node, String dummy) {
        boolean childrenChanged = false;
        for (var child : node.getChildren()) {
            childrenChanged |= visit(child);
        }
        changed |= childrenChanged;
        return childrenChanged;
    }

    @Override
    protected void buildVisitor() {
        // Already configured in constructor
    }
}
