package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
import org.specs.comp.ollir.LiteralElement;
import org.specs.comp.ollir.inst.AssignInstruction;
import org.specs.comp.ollir.inst.BinaryOpInstruction;
import org.specs.comp.ollir.inst.ReturnInstruction;
import org.specs.comp.ollir.inst.SingleOpInstruction;
import org.specs.comp.ollir.tree.TreeNode;

import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JasminGenerator com instruções low-cost (iconst_*, bipush, sipush, iload_*, istore_*, iinc, etc.).
 * Não usa Type do OLLIR; tudo é tratado como inteiro ("i").
 */
public class JasminGenerator {

    private static final String NL  = "\n";
    private static final String TAB = "    ";

    private final OllirResult ollirResult;
    private final List<Report> reports;
    private final JasminUtils utils;
    private final FunctionClassMap<TreeNode, String> gens;

    private Method currentMethod;
    private String code;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        this.reports    = new ArrayList<>();
        this.utils      = new JasminUtils(ollirResult);
        this.gens       = new FunctionClassMap<>();

        gens.put(ClassUnit.class,            this::genClassUnit);
        gens.put(Method.class,               this::genMethod);
        gens.put(AssignInstruction.class,    this::genAssign);
        gens.put(SingleOpInstruction.class,  this::genSingleOp);
        gens.put(LiteralElement.class,       this::genLiteral);
        gens.put(Operand.class,              this::genOperand);
        gens.put(BinaryOpInstruction.class,  this::genBinaryOp);
        gens.put(ReturnInstruction.class,    this::genReturn);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }
        return code;
    }

    private String apply(TreeNode node) {
        return gens.apply(node);
    }

    private String genClassUnit(ClassUnit cu) {
        var sb = new StringBuilder();
        sb.append(".class ").append(cu.getClassName()).append(NL)
                .append(".super java/lang/Object").append(NL).append(NL);

        sb.append("; default constructor").append(NL)
                .append(".method public <init>()V").append(NL)
                .append(TAB).append("aload_0").append(NL)
                .append(TAB).append("invokespecial java/lang/Object/<init>()V").append(NL)
                .append(TAB).append("return").append(NL)
                .append(".end method").append(NL).append(NL);

        for (var m : cu.getMethods()) {
            if (!m.isConstructMethod()) {
                sb.append(apply(m));
            }
        }
        return sb.toString();
    }

    private String genMethod(Method m) {
        this.currentMethod = m;
        var sb = new StringBuilder();

        // hard-coded params e retorno como inteiro
        String params = "I";
        String ret    = "I";

        sb.append(NL)
                .append(".method ")
                .append(utils.getModifier(m.getMethodAccessModifier()))
                .append(m.getMethodName())
                .append("(").append(params).append(")").append(ret)
                .append(NL);

        sb.append(TAB).append(".limit stack 99").append(NL);
        sb.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : m.getInstructions()) {
            String body = StringLines
                    .getLines(apply(inst))
                    .stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            sb.append(body);
        }

        sb.append(".end method").append(NL);
        this.currentMethod = null;
        return sb.toString();
    }

    private String genAssign(AssignInstruction ai) {
        var sb = new StringBuilder();
        // deteta iinc: x = x + c ou x = x - c
        var rhs = ai.getRhs(); // Element direto
        if (rhs instanceof BinaryOpInstruction bin
                && bin.getLeftOperand() instanceof Operand var
                && bin.getRightOperand() instanceof LiteralElement lit
                && var.getName().equals(((Operand) ai.getDest()).getName())) {
            int idx = currentMethod.getVarTable().get(var.getName()).getVirtualReg();
            int c   = Integer.parseInt(lit.getLiteral());
            if (bin.getOperation().getOpType().name().equals("SUB")) {
                c = -c;
            }
            sb.append("iinc ").append(idx).append(" ").append(c).append(NL);
            return sb.toString();
        }

        // caso geral: carrega RHS e depois armazena no LHS
        sb.append(apply(rhs));
        Operand dest = (Operand) ai.getDest();
        int idx = currentMethod.getVarTable().get(dest.getName()).getVirtualReg();
        sb.append(utils.getStoreInstruction("i", idx)).append(NL);
        return sb.toString();
    }

    private String genSingleOp(SingleOpInstruction so) {
        return apply(so.getSingleOperand());
    }

    private String genLiteral(LiteralElement lit) {
        int v = Integer.parseInt(lit.getLiteral());
        return utils.getConstInstruction(v) + NL;
    }

    private String genOperand(Operand op) {
        int idx = currentMethod.getVarTable().get(op.getName()).getVirtualReg();
        return utils.getLoadInstruction("i", idx) + NL;
    }

    private String genBinaryOp(BinaryOpInstruction bin) {
        var sb = new StringBuilder();
        sb.append(apply(bin.getLeftOperand()));
        sb.append(apply(bin.getRightOperand()));
        String opType = bin.getOperation().getOpType().name();
        String instr;
        switch (opType) {
            // aritmética
            case "ADD": instr = "iadd"; break;
            case "SUB": instr = "isub"; break;
            case "MUL": instr = "imul"; break;
            case "DIV": instr = "idiv"; break;
            case "REM": instr = "irem"; break;

            // comparações contra zero (se o OLLIR usar BinOp LTH, GTH, …)
            case "LTH": instr = "iflt"; break;
            case "GTH": instr = "ifgt"; break;
            case "LE":  instr = "ifle"; break;
            case "GE":  instr = "ifge"; break;
            case "EQ":  instr = "ifeq"; break;
            case "NE":  instr = "ifne"; break;

            default:
                throw new RuntimeException("Op não suportada: " + opType);
        }
        sb.append(instr).append(NL);
        return sb.toString();
    }

    private String genReturn(ReturnInstruction ri) {
        if (ri.getOperand() != null && ri.getOperand().isPresent()) {
            // unwrap do Optional<Element> e cast para TreeNode
            var elem = ri.getOperand().get();
            return apply((TreeNode) elem) + "ireturn" + NL;
        }
        return "return" + NL;
    }

}
