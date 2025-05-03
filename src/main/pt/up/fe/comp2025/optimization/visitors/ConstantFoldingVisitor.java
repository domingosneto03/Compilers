package pt.up.fe.comp2025.optimization.visitors;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

/**
 * Implements constant folding optimization.
 */
public class ConstantFoldingVisitor extends PreorderJmmVisitor<Void, Boolean> {

    @Override
    protected void buildVisitor() {
        addVisit("BinaryExpr", this::foldBinary);
        setDefaultVisit((n, __) -> false);
    }

    private Boolean foldBinary(JmmNode node, Void unused) {

        var left = node.getChild(0);
        var right = node.getChild(1);

        // Só tentamos avaliar se ambos os filhos são literais inteiros
        if (!left.getKind().equals("IntegerLiteral") || !right.getKind().equals("IntegerLiteral")) {
            return false;
        }

        int lhs = Integer.parseInt(left.get("value"));
        int rhs = Integer.parseInt(right.get("value"));
        String op = node.get("op");

        Integer result;
        switch (op) {
            case "+": result = lhs + rhs; break;
            case "-": result = lhs - rhs; break;
            case "*": result = lhs * rhs; break;
            case "/":
                if (rhs == 0) return false; // evitar divisão por zero
                result = lhs / rhs;
                break;
            default: return false; // operador não suportado
        }

        // Transforma este nó num IntegerLiteral com o resultado
        node.put("kind", "IntegerLiteral");
        node.put("value", String.valueOf(result));
        node.getChildren().clear(); // remove filhos pois agora é literal
        System.out.println("💥 Constant folding aplicado em: " + node);


        return true;
    }

}

