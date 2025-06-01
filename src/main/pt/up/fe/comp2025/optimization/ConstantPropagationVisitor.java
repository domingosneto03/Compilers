package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

import java.util.*;

/**
 * Conservative Constant Propagation that handles loops correctly.
 * Variables modified inside loops are not considered constant outside the loop.
 */
public class ConstantPropagationVisitor extends PreorderJmmVisitor<String, Void> {

    private final SymbolTable table;
    private boolean changed;
    private String currentMethod;

    private Map<String, ConstantValue> constantMap;

    private Set<String> loopModifiedVars;

    private boolean insideLoop;

    public ConstantPropagationVisitor(SymbolTable table) {
        this.table = table;
        this.changed = false;
        this.constantMap = new HashMap<>();
        this.loopModifiedVars = new HashSet<>();
        this.insideLoop = false;
        setDefaultVisit(this::defaultVisit);
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit("WhileStmt", this::visitWhileStmt);
        addVisit("AssignStmt", this::visitAssignStmt);
        addVisit("VarRefExpr", this::visitVarRefExpr);
    }

    public boolean didChange() {
        return changed;
    }

    private Void visitMethodDecl(JmmNode methodDecl, String dummy) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;

        constantMap.clear();
        loopModifiedVars.clear();
        insideLoop = false;

        System.out.println("Starting conservative constant propagation for method: " + currentMethod);

        identifyLoopModifiedVariables(methodDecl);

        for (var child : methodDecl.getChildren()) {
            visit(child);
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode whileStmt, String dummy) {
        System.out.println("Entering while loop - disabling aggressive propagation");

        boolean wasInsideLoop = insideLoop;
        insideLoop = true;

        // Visit condition
        visit(whileStmt.getChild(0));

        // Visit body
        visit(whileStmt.getChild(1));

        insideLoop = wasInsideLoop;

        System.out.println("Exiting while loop");
        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, String dummy) {
        if (assignStmt.getNumChildren() != 2) {
            return defaultVisit(assignStmt, dummy);
        }

        JmmNode lhs = assignStmt.getChild(0);
        JmmNode rhs = assignStmt.getChild(1);

        if (!lhs.getKind().equals("VarRefExpr")) {
            visit(rhs);
            return null;
        }

        String varName = lhs.get("value");

        visit(rhs);

        // Check if this variable was modified in a loop
        if (loopModifiedVars.contains(varName)) {
            System.out.println("Variable '" + varName + "' modified in loop - not constant");
            constantMap.remove(varName);
            return null;
        }

        // Check if RHS is a constant after visiting it
        ConstantValue constantValue = getConstantValue(rhs);

        if (constantValue != null && !insideLoop) {
            constantMap.put(varName, constantValue);
            System.out.println("Variable '" + varName + "' assigned constant: " + constantValue);
        } else {
            constantMap.remove(varName);
            if (insideLoop) {
                System.out.println("Variable '" + varName + "' assigned inside loop - not constant");
            } else {
                System.out.println("Variable '" + varName + "' assigned non-constant value");
            }
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, String dummy) {
        String varName = varRefExpr.get("value");

        JmmNode parent = varRefExpr.getParent();
        if (parent != null && parent.getKind().equals("AssignStmt")) {
            if (parent.getChild(0) == varRefExpr) {
                return null;
            }
        }

        if (loopModifiedVars.contains(varName)) {
            return null;
        }

        ConstantValue constantValue = constantMap.get(varName);

        if (constantValue != null) {
            if (insideLoop) {
                System.out.println("Skipping propagation inside loop for: " + varName);
                return null;
            }

            JmmNode replacement = createConstantNode(constantValue);

            System.out.println("Propagating constant: " + varName + " -> " + constantValue.value);

            varRefExpr.replace(replacement);
            changed = true;
        }

        return null;
    }

    /**
     * First pass to identify variables that are modified inside loops
     */
    private void identifyLoopModifiedVariables(JmmNode node) {
        if (node.getKind().equals("WhileStmt")) {
            JmmNode loopBody = node.getChild(1);
            findModifiedVariables(loopBody, loopModifiedVars);
        }

        for (var child : node.getChildren()) {
            identifyLoopModifiedVariables(child);
        }
    }

    /**
     * Find all variables that are assigned in a given subtree
     */
    private void findModifiedVariables(JmmNode node, Set<String> modifiedVars) {
        if (node.getKind().equals("AssignStmt") && node.getNumChildren() >= 2) {
            JmmNode lhs = node.getChild(0);
            if (lhs.getKind().equals("VarRefExpr")) {
                String varName = lhs.get("value");
                modifiedVars.add(varName);
                System.out.println("Found loop-modified variable: " + varName);
            }
        }

        for (var child : node.getChildren()) {
            findModifiedVariables(child, modifiedVars);
        }
    }

    private Void defaultVisit(JmmNode node, String dummy) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return null;
    }

    /**
     * Determines if a node represents a constant value
     */
    private ConstantValue getConstantValue(JmmNode node) {
        switch (node.getKind()) {
            case "IntegerLiteral":
                return new ConstantValue(ConstantType.INTEGER, node.get("value"));
            case "BooleanTrue":
                return new ConstantValue(ConstantType.BOOLEAN, "true");
            case "BooleanFalse":
                return new ConstantValue(ConstantType.BOOLEAN, "false");
            case "VarRefExpr":
                String varName = node.get("value");
                return constantMap.get(varName);
            default:
                return null;
        }
    }

    /**
     * Creates a new constant node based on the constant value
     */
    private JmmNode createConstantNode(ConstantValue constantValue) {
        JmmNode node;

        switch (constantValue.type) {
            case INTEGER:
                node = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
                node.put("value", constantValue.value);
                break;
            case BOOLEAN:
                if ("true".equals(constantValue.value)) {
                    node = new JmmNodeImpl(Collections.singletonList("BooleanTrue"));
                    node.put("value", "true");
                } else {
                    node = new JmmNodeImpl(Collections.singletonList("BooleanFalse"));
                    node.put("value", "false");
                }
                break;
            default:
                throw new RuntimeException("Unsupported constant type: " + constantValue.type);
        }

        return node;
    }

    @Override
    protected void buildVisitor() {
        // Already configured in constructor
    }

    /**
     * Represents a constant value with its type
     */
    private static class ConstantValue {
        final ConstantType type;
        final String value;

        ConstantValue(ConstantType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }

    private enum ConstantType {
        INTEGER, BOOLEAN
    }
}