package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {

        reports = new ArrayList<>();

        // TODO: After your grammar supports more things inside the program (e.g., imports) you will have to change this
        var classDecl = root.getChild(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        String extendedClass = "";
        if (classDecl.hasAttribute("extendedClass")){
            extendedClass = classDecl.get("extendedClass");
        }

        var fields = getLocalsList(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var imports = buildImports(root);

        return new JmmSymbolTable(className, extendedClass, fields, methods, returnTypes, params, locals,imports);
    }


    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            // TODO: After you add more types besides 'int', you will have to update this
            var returnType = TypeUtils.newIntType();
            map.put(name, returnType);
        }

        return map;
    }


    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            var params = method.getChildren(PARAM).stream()
                    // TODO: When you support new types, this code has to be updated
                    .map(param -> new Symbol(TypeUtils.newIntType(), param.get("name")))
                    .toList();

            map.put(name, params);
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            var locals = method.getChildren(VAR_DECL).stream()
                    // TODO: When you support new types, this code has to be updated
                    .map(varDecl -> new Symbol(TypeUtils.newIntType(), varDecl.get("name")))
                    .toList();


            map.put(name, locals);
        }

        return map;
    }

    private List<String> buildMethods(JmmNode classDecl) {

        var methods = classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();

        return methods;
    }


    private static List<String> buildImports(JmmNode root) {

        List<String> imports = new ArrayList<>();

        List<JmmNode> rootChildren = root.getChildren();
        for (JmmNode child : rootChildren){
            if(Objects.equals(child.getKind(), "ImportStmt")) {
                imports.add(child.get("ID"));
            }
        }

        return imports;
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {


        List<Symbol> Locals = new ArrayList<>();
        methodDecl.getChildren(VAR_DECL).stream().forEach(varDecl -> {
            boolean isArray = Objects.equals(varDecl.getChild(0).getKind(), "VarArray");
            Type type = new Type(varDecl.getChild(0).get("name"),isArray);
            String name = varDecl.get("name");
            Locals.add(new Symbol(type,name));
        }  );

        return Locals;
    }



}
