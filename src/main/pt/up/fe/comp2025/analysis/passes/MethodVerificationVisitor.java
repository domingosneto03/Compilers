package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;

public class MethodVerificationVisitor extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit("MethodCallExpr", this::checkMethodCall);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        return null;
    }

    private Void checkMethodCall(JmmNode callNode, SymbolTable table) {
        String methodName = callNode.get("method");

        TypeUtils typeUtils = new TypeUtils(table);

        Type callerType;
        try {
            callerType = typeUtils.getExprType(callNode.getChild(0), currentMethod);
        } catch (RuntimeException e) {
            addReport(newError(callNode, "Failed to resolve caller type: " + e.getMessage()));
            return null;
        }

        boolean declaredLocally = table.getMethods().contains(methodName);
        boolean fromImport = table.getImports().stream().anyMatch(imp -> imp.endsWith("." + callerType.getName()) || imp.equals(callerType.getName()));
        boolean fromSuperclass = table.getSuper() != null && !table.getSuper().isEmpty();

        if (!declaredLocally && (fromImport || fromSuperclass)) {
            return null; // Assume method is valid
        }

        if (!declaredLocally) {
            addReport(newError(callNode, "Method '" + methodName + "' not declared in class or imports."));
            return null;
        }

        List<JmmNode> argNodes = callNode.getChildren().subList(1, callNode.getNumChildren());
        List<Type> argTypes = argNodes.stream()
                .map(arg -> {
                    try {
                        return typeUtils.getExprType(arg, currentMethod);
                    } catch (Exception e) {
                        addReport(newError(arg, "Could not determine argument type: " + e.getMessage()));
                        return null;
                    }
                })
                .filter(type -> type != null)
                .collect(Collectors.toList());

        List<Symbol> methodParams = table.getParameters(methodName);

        boolean hasVarargs = !methodParams.isEmpty() &&
                methodParams.get(methodParams.size() - 1).getType().isArray();

        if (hasVarargs) {
            int fixedArgs = methodParams.size() - 1;
            if (argTypes.size() < fixedArgs) {
                addReport(newError(callNode, "Too few arguments for varargs method '" + methodName + "'"));
                return null;
            }
            for (int i = 0; i < fixedArgs; i++) {
                Type expected = methodParams.get(i).getType();
                Type actual = argTypes.get(i);
                if (!expected.equals(actual)) {
                    addReport(newError(callNode, String.format("Arg %d type mismatch in '%s': expected %s, got %s",
                            i + 1, methodName, expected, actual)));
                }
            }
        } else {
            if (argTypes.size() != methodParams.size()) {
                addReport(newError(callNode, "Argument count mismatch in method '" + methodName + "': expected " +
                        methodParams.size() + ", got " + argTypes.size()));
                return null;
            }

            for (int i = 0; i < methodParams.size(); i++) {
                Type expected = methodParams.get(i).getType();
                Type actual = argTypes.get(i);
                if (!expected.equals(actual)) {
                    addReport(newError(callNode, String.format("Arg %d type mismatch in '%s': expected %s, got %s",
                            i + 1, methodName, expected, actual)));
                }
            }
        }

        return null;
    }


}