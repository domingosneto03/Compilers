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

        // Instead of using root.getChild(0), filter the children to find the class declaration.
        Optional<JmmNode> maybeClassDecl = root.getChildren().stream()
                .filter(child -> Kind.CLASS_DECL.check(child))
                .findFirst();

        // If no class declaration is found, throw an error.
        SpecsCheck.checkArgument(maybeClassDecl.isPresent(),
                () -> "Expected a class declaration, but got: " + root.getChildren());
        JmmNode classDecl = maybeClassDecl.get();
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

        return new JmmSymbolTable(className, extendedClass, fields, methods, returnTypes, params, locals, imports);
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> returnTypes = new HashMap<>();

        // Iterate over each method declaration in the class
        for (var method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = method.get("name");

            // If the method is the main method, its return type is void
            if (Objects.equals(methodName, "main")) {
                returnTypes.put(methodName, new Type("void", false));
            } else {
                // The first child of a non-main method is the return type node.
                JmmNode typeNode = method.getChild(0);

                // The grammar defines the type using the "value" attribute:
                //   - For #Var, it is a simple type.
                //   - For #VarArray, it represents an array type.
                //   - For #VarArgs, it is treated similarly to an array.
                String typeName = typeNode.get("value");

                // Check the node's kind to determine if it is an array
                boolean isArray = typeNode.getKind().equals("VarArray") || typeNode.getKind().equals("VarArgs");

                // Store the method's return type in the map
                returnTypes.put(methodName, new Type(typeName, isArray));
            }
        }

        return returnTypes;
    }


    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> paramsMap = new HashMap<>();

        // Iterate over every method declaration in the class
        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = method.get("name");
            List<Symbol> paramsList = new ArrayList<>();

            // Assume that the AST builder creates a node labeled "PARAM" for each parameter.
            for (JmmNode param : method.getChildren("ParamExp")) {
                // The first child of the parameter node is the type node
                JmmNode typeNode = param.getChild(0);
                // Retrieve the type name from the attribute "value" (set by the grammar)
                String typeName = typeNode.get("value");
                // Determine if the parameter is an array type by checking the node kind
                boolean isArray = typeNode.getKind().equals("VarArray") || typeNode.getKind().equals("VarArgs");
                // Retrieve the parameter name from the attribute "name" of the parameter node
                String paramName = param.get("name");
                // Create a new Symbol for the parameter and add it to the list
                paramsList.add(new Symbol(new Type(typeName, isArray), paramName));
            }
            paramsMap.put(methodName, paramsList);
        }
        return paramsMap;
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
<<<<<<< HEAD

=======
        List<String> imports = new ArrayList<>();

        // Iterate over all children of the root node
        for (JmmNode child : root.getChildren()) {
            // Check if the node is an import statement (as defined by our grammar)
            if ("ImportStmt".equals(child.getKind())) {
                // Build the full import string using the first ID and any additional parts
                StringBuilder importBuilder = new StringBuilder();
                // The first part is stored as an attribute "ID" in the ImportStmt node
                importBuilder.append(child.get("ID"));
                // Any additional parts (after a dot) are stored as children nodes
                for (JmmNode subNode : child.getChildren()) {
                    importBuilder.append(".").append(subNode.get("ID"));
                }
                imports.add(importBuilder.toString());
            }
        }
        return imports;
>>>>>>> luis
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        List<Symbol> locals = new ArrayList<>();

        // Iterate over all varDecl nodes within the method declaration
        for (JmmNode varDecl : methodDecl.getChildren(VAR_DECL)) {
            // Get the type node (first child of the varDecl)
            JmmNode typeNode = varDecl.getChild(0);

            // Check if the type is an array, either VarArray or VarArgs
            boolean isArray = typeNode.getKind().equals("VarArray") || typeNode.getKind().equals("VarArgs");

            // Retrieve the type name from the "value" attribute (not "name")
            String typeName = typeNode.get("value");

            // Retrieve the variable name from the varDecl node (assumed to be set in attribute "name")
            String varName = varDecl.get("name");

            // Create a new Symbol with the type and variable name, and add it to the list
            locals.add(new Symbol(new Type(typeName, isArray), varName));
        }

        return locals;
    }



}
