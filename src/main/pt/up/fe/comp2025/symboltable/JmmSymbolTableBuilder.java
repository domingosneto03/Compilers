package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    private List<Report> reports;

    /** Retorna os erros semânticos detetados na construção da tabela */
    public List<Report> getReports() {
        return reports;
    }

    /** Cria um Report de erro semântico */
    private static Report newError(JmmNode node, String msg) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                msg,
                null
        );
    }

    /** Constrói a symbol table, preenchendo `reports` com eventuais erros de duplicados */
    public JmmSymbolTable build(JmmNode root) {
        reports = new ArrayList<>();

        // 1) Encontrar CLASS_DECL
        Optional<JmmNode> maybeClass = root.getChildren().stream()
                .filter(Kind.CLASS_DECL::check)
                .findFirst();
        SpecsCheck.checkArgument(
                maybeClass.isPresent(),
                () -> "Esperava CLASS_DECL, mas só encontrei: " + root.getChildren()
        );
        JmmNode classDecl = maybeClass.get();
        String className = classDecl.get("name");
        String extendedClass = classDecl.hasAttribute("extendedClass")
                ? classDecl.get("extendedClass")
                : "";

        // 2) Campos (VAR_DECL fora de métodos) com duplicados
        List<Symbol> fields = new ArrayList<>();
        {
            Set<String> seen = new HashSet<>();
            for (JmmNode var : classDecl.getChildren(VAR_DECL)) {
                String name = var.get("name");
                Type t = TypeUtils.convertType(var.getChild(0));
                if (!seen.add(name)) {
                    reports.add(newError(var,
                            "Campo duplicado na SymbolTable: '" + name + "'"));
                }
                fields.add(new Symbol(t, name));
            }
        }

        // 3) Métodos
        List<String> methods = new ArrayList<>();
        for (JmmNode meth : classDecl.getChildren(METHOD_DECL)) {
            methods.add(meth.get("name"));
        }

        // 4) Return types
        Map<String, Type> returnTypes = new LinkedHashMap<>();
        for (JmmNode meth : classDecl.getChildren(METHOD_DECL)) {
            String mName = meth.get("name");
            Optional<JmmNode> r = meth.getChildren().stream()
                    .filter(n -> n.getKind().equals("Var")
                            || n.getKind().equals("VarArray")
                            || n.getKind().equals("VarArgs"))
                    .findFirst();
            Type rt = r.map(TypeUtils::convertType)
                    .orElseGet(() -> new Type("void", false));
            returnTypes.put(mName, rt);
        }

        // 5) Parâmetros com duplicados
        Map<String, List<Symbol>> params = new LinkedHashMap<>();
        for (JmmNode meth : classDecl.getChildren(METHOD_DECL)) {
            String mName = meth.get("name");
            List<Symbol> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JmmNode param : meth.getChildren(Kind.PARAM)) {
                String name = param.get("name");
                Type t = TypeUtils.convertType(param.getChild(0));
                if (!seen.add(name)) {
                    reports.add(newError(param,
                            "Parâmetro duplicado em '" + mName + "': '" + name + "'"));
                }
                list.add(new Symbol(t, name));
            }
            params.put(mName, list);
        }

        // 6) Locais com duplicados
        Map<String, List<Symbol>> locals = new LinkedHashMap<>();
        for (JmmNode meth : classDecl.getChildren(METHOD_DECL)) {
            String mName = meth.get("name");
            List<Symbol> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JmmNode var : meth.getChildren(VAR_DECL)) {
                if (var.getChildren().isEmpty()) continue;
                String name = var.get("name");
                Type t = TypeUtils.convertType(var.getChild(0));
                if (!seen.add(name)) {
                    reports.add(newError(var,
                            "Variável local duplicada em '" + mName + "': '" + name + "'"));
                }
                list.add(new Symbol(t, name));
            }
            locals.put(mName, list);
        }

        // 7) Imports
        List<String> imports = new ArrayList<>();
        for (JmmNode imp : root.getChildren()) {
            if (Kind.IMPORT_STMT.check(imp)) {
                StringBuilder sb = new StringBuilder(imp.get("ID"));
                for (JmmNode sub : imp.getChildren()) {
                    sb.append(".").append(sub.get("ID"));
                }
                imports.add(sb.toString());
            }
        }

        // 8) Constrói a JmmSymbolTable com todos os dados
        return new JmmSymbolTable(
                className, extendedClass,
                fields, methods,
                returnTypes, params, locals,
                imports
        );
    }
}
