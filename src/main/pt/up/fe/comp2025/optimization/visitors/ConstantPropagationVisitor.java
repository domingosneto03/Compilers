package pt.up.fe.comp2025.optimization.visitors;

import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class ConstantPropagationVisitor extends PreorderJmmVisitor<Void, Boolean> {

    private final Map<String, Integer> constants = new HashMap<>();
    private final Map<String, Integer> variableUsageCount = new HashMap<>();

    @Override
    protected void buildVisitor() {
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(VAR_REF_EXPR, this::visitVarRefExpr);
        setDefaultVisit(this::defaultVisit);
    }

    private Boolean visitAssignStmt(JmmNode node, Void unused) {
        var leftChild = node.getChild(0);

        if (!leftChild.getKind().equals(VAR_REF_EXPR.getNodeName())) {
            return false;
        }

        var lhs = leftChild.get("value");
        var rhs = node.getChild(1);

        if (INTEGER_LITERAL.check(rhs)) {
            int value = Integer.parseInt(rhs.get("value"));
            constants.put(lhs, value);
            variableUsageCount.put(lhs, 0);
            System.out.println(">>> CONSTANTE DETETADA: " + lhs + " = " + value);
        }

        // FORÇA remoção sempre para testar
        JmmNode wrapper = node.getParent();
        JmmNode parent = wrapper.getParent();
        int index = parent.getChildren().indexOf(wrapper);
        if (index != -1) {
            parent.removeChild(index);
            System.out.println(">>> REMOVIDO wrapper do assign a '" + lhs + "'");
            return true;
        }

        return false;
    }


    private Boolean visitVarRefExpr(JmmNode node, Void unused) {
        var varName = node.get("value");
        variableUsageCount.put(varName, variableUsageCount.getOrDefault(varName, 0) + 1);

        if (constants.containsKey(varName)) {
            node.put("kind", INTEGER_LITERAL.getNodeName());
            node.put("value", Integer.toString(constants.get(varName)));
            return true;
        }
        return false;
    }

    private Boolean defaultVisit(JmmNode node, Void unused) {
        boolean changed = false;
        for (var child : node.getChildren()) {
            changed |= visit(child);
        }
        return changed;
    }

    private void updateUsageCounts(JmmNode rootNode) {
        variableUsageCount.clear();
        countVariableUsages(rootNode);
    }

    private void countVariableUsages(JmmNode node) {
        if (VAR_REF_EXPR.check(node)) {
            var varName = node.get("value");
            variableUsageCount.put(varName, variableUsageCount.getOrDefault(varName, 0) + 1);
        }
        for (var child : node.getChildren()) {
            countVariableUsages(child);
        }
    }

    private boolean removeUnusedAssignments(JmmNode assignStmt, String varName) {
        if (variableUsageCount.getOrDefault(varName, 0) == 0) {
            // Vai ao pai (tipo OtherStmt)
            JmmNode wrapper = assignStmt.getParent();
            JmmNode parent = wrapper.getParent();

            // Verifica se o wrapper (e não o assign) está dentro da lista de filhos do pai
            List<JmmNode> children = new ArrayList<>(parent.getChildren());
            System.out.println(">> Verificação de remoção para '" + varName + "' → usos = " + variableUsageCount.getOrDefault(varName, 0));
            System.out.println("   ↳ Parent do assign: " + assignStmt.getParent().getKind());
            System.out.println("   ↳ Pai do parent (esperado MethodDecl): " + assignStmt.getParent().getParent().getKind());

            for (JmmNode child : children) {
                if (child == wrapper) {
                    parent.removeChild(child);
                    System.out.println(">>> Removido assign COMPLETO de " + varName);
                    System.out.println(">>> REMOVER filho: " + wrapper.getKind() + " de " + parent.getKind());

                    return true;
                }
            }
        }
        return false;
    }

}

