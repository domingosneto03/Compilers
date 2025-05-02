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

        var fields = getFieldsList(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var imports = buildImports(root);

        return new JmmSymbolTable(className, extendedClass, fields, methods, returnTypes, params, locals, imports);
    }

    private boolean hasValidReturnType(JmmNode method) {
        // Check if this is a non-void method by looking for the type declaration
        return method.getChildren().stream()
                .anyMatch(node -> TYPE.check(node) || VAR_ARRAY.check(node) || VAR_ARGS.check(node));
    }

    private String extractMethodName(JmmNode method) {
        // Get method name or default to "main" for main method
        return method.hasAttribute("name") ? method.get("name") : "main";
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> returnTypes = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = extractMethodName(method);

            if (hasValidReturnType(method)) {
                JmmNode returnTypeNode = method.getChildren().stream()
                        .filter(node -> node.getKind().equals("Var")
                                || node.getKind().equals("VarArray")
                                || node.getKind().equals("VarArgs"))
                        .findFirst()
                        .orElseThrow(() -> new NotImplementedException("Expected a valid return type for method: " + methodName));

                // Use TypeUtils.convertType to get the method's return type.
                returnTypes.put(methodName, TypeUtils.convertType(returnTypeNode));
            } else {
                returnTypes.put(methodName, new Type("void", false));
            }
        }

        return returnTypes;
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> paramsMap = new HashMap<>();
        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = extractMethodName(method);
            List<Symbol> paramsList = new ArrayList<>();

            for (JmmNode param : method.getChildren("ParamExp")) {
                JmmNode typeNode = param.getChild(0);
                paramsList.add(new Symbol(TypeUtils.convertType(typeNode), param.get("name")));
            }
            paramsMap.put(methodName, paramsList);
        }
        return paramsMap;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> localsMap = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = method.get("name");
            List<Symbol> locals = new ArrayList<>();

            // Collect explicit variable declarations
            for (JmmNode varDecl : method.getChildren(VAR_DECL)) {
                Type type = TypeUtils.convertType(varDecl.getChild(0));
                String varName = varDecl.get("name");
                locals.add(new Symbol(type, varName));
            }

            // Collect loop variables and other implicit declarations
            collectImplicitVariables(method, locals, methodName);

            localsMap.put(methodName, locals);
        }

        return localsMap;
    }

    // Helper method to collect variables from loops and assignments
    private void collectImplicitVariables(JmmNode node, List<Symbol> locals, String methodName) {
        // Process for loop initializers
        if (node.getKind().equals(FOR_STMT.getNodeName()) && node.getNumChildren() > 0) {
            JmmNode initNode = node.getChild(0);
            if (initNode.getKind().equals(ASSIGN_STMT.getNodeName())) {
                String varName = initNode.get("name");
                // Only add if not already present
                if (!isVariableDeclared(varName, locals)) {
                    Type inferredType = inferTypeFromAssignment(initNode);
                    locals.add(new Symbol(inferredType, varName));
                }
            }
        }

        // Process assignment statements that might introduce variables
        if (node.getKind().equals(ASSIGN_STMT.getNodeName())) {
            String varName = node.get("name");
            if (!isVariableDeclared(varName, locals)) {
                Type inferredType = inferTypeFromAssignment(node);
                locals.add(new Symbol(inferredType, varName));
            }
        }

        // Process while loop variables that might be used
        if (node.getKind().equals(WHILE_STMT.getNodeName())) {
            collectVariablesFromExpr(node.getChild(0), locals);
        }

        // Recursively process children
        for (JmmNode child : node.getChildren()) {
            collectImplicitVariables(child, locals, methodName);
        }
    }

    // Helper to collect variables referenced in expressions
    private void collectVariablesFromExpr(JmmNode exprNode, List<Symbol> locals) {
        if (exprNode.getKind().equals(VAR_REF_EXPR.getNodeName())) {
            String varName = exprNode.get("value");
            if (!isVariableDeclared(varName, locals)) {
                // If we see a variable reference but it's not declared, assume int type
                locals.add(new Symbol(TypeUtils.newIntType(), varName));
            }
        }

        // Recursively check children expressions
        for (JmmNode child : exprNode.getChildren()) {
            collectVariablesFromExpr(child, locals);
        }
    }

    // Helper to check if a variable is already declared
    private boolean isVariableDeclared(String varName, List<Symbol> symbols) {
        return symbols.stream().anyMatch(s -> s.getName().equals(varName));
    }

    // Infer type from an assignment expression
    private Type inferTypeFromAssignment(JmmNode assignNode) {
        if (assignNode.getNumChildren() >= 2) {
            JmmNode rhsExpr = assignNode.getChild(1);
            if (rhsExpr.getKind().equals(INTEGER_LITERAL.getNodeName())) {
                return TypeUtils.newIntType();
            } else if (rhsExpr.getKind().equals(BOOLEAN_TRUE.getNodeName()) ||
                    rhsExpr.getKind().equals(BOOLEAN_FALSE.getNodeName())) {
                return new Type("boolean", false);
            } else if (rhsExpr.getKind().equals(NEW_INT_ARRAY_EXPR.getNodeName())) {
                return new Type("int", true);
            } else if (rhsExpr.getKind().equals(NEW_OBJECT_EXPR.getNodeName())) {
                String className = rhsExpr.get("value");
                return new Type(className, false);
            }
        }

        // Default to int type if we can't determine
        return TypeUtils.newIntType();
    }

    private List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = new ArrayList<>();
        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            methods.add(extractMethodName(method));
        }
        return methods;
    }

    private static List<String> buildImports(JmmNode root) {
        List<String> imports = new ArrayList<>();
        for (JmmNode importNode : root.getChildren(IMPORT_STMT)) {
            // Extract all name parts from the import statement
            List<String> nameParts = new ArrayList<>();
            if (importNode.hasAttribute("name")) {
                // For single import name
                nameParts.add(importNode.get("name"));
            } else {
                // For imports with multiple parts (using the "name" list attribute)
                for (int i = 0; i < importNode.getNumChildren(); i++) {
                    nameParts.add(importNode.getChild(i).get("value"));
                }
            }
            imports.add(String.join(".", nameParts));
        }
        return imports;
    }

    private static List<Symbol> getFieldsList(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        for (JmmNode varDecl : classDecl.getChildren(VAR_DECL)) {
            JmmNode typeNode = varDecl.getChild(0);
            fields.add(new Symbol(TypeUtils.convertType(typeNode), varDecl.get("name")));
        }

        return fields;
    }
}
