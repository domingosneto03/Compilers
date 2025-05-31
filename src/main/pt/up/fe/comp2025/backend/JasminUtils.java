package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

import org.specs.comp.ollir.type.Type;

public class JasminUtils {

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }

    public String getModifier(AccessModifier accessModifier) {
        return switch (accessModifier) {
            case PUBLIC -> "public ";
            case PRIVATE -> "private ";
            case PROTECTED -> "protected ";
            default -> "";
        };
    }

    public String getJasminType(Object type) {
        if (type == null) {
            return "V"; // void
        }

        String typeStr = type.toString();

        // Handle OLLIR Type objects
        if (type instanceof Type) {
            return convertOllirType((Type) type);
        }

        // Handle string representations
        typeStr = typeStr.toLowerCase();

        // Handle primitive types
        if (typeStr.contains("int32") || typeStr.equals("i32") || typeStr.contains(".i32")) {
            return "I";
        }

        if (typeStr.contains("bool") || typeStr.equals("boolean") || typeStr.contains(".bool")) {
            return "Z";
        }

        if (typeStr.contains("string") || typeStr.equals("string")) {
            return "Ljava/lang/String;";
        }

        if (typeStr.contains("void") || typeStr.equals("v") || typeStr.contains(".v")) {
            return "V";
        }

        // Handle arrays
        if (typeStr.contains("array")) {
            if (typeStr.contains("int") || typeStr.contains("i32")) {
                return "[I";
            } else if (typeStr.contains("string")) {
                return "[Ljava/lang/String;";
            } else {
                // Generic object array
                return "[Ljava/lang/Object;";
            }
        }

        // Handle class types
        if (typeStr.contains("this") || typeStr.contains("object")) {
            // Use current class name
            return "L" + ollirResult.getOllirClass().getClassName() + ";";
        }

        // Handle specific class names
        if (typeStr.matches(".*\\b[A-Z][a-zA-Z0-9_]*\\b.*")) {
            // Extract class name
            String className = extractClassName(typeStr);
            if (className != null && !className.isEmpty()) {
                return "L" + className + ";";
            }
        }

        // Fallback
        return "Ljava/lang/Object;";
    }

    private String convertOllirType(Type ollirType) {
        if (ollirType instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) ollirType;
            String elementType = convertOllirType(arrayType.getElementType());
            return "[" + elementType;
        }

        if (ollirType instanceof ClassType) {
            ClassType classType = (ClassType) ollirType;
            String className = classType.getName();

            // Handle primitive wrapper classes
            return switch (className) {
                case "String" -> "Ljava/lang/String;";
                case "Object" -> "Ljava/lang/Object;";
                default -> "L" + className + ";";
            };
        }

        if (ollirType instanceof BuiltinType) {
            BuiltinType builtinType = (BuiltinType) ollirType;
            return switch (builtinType.getKind()) {
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case STRING -> "Ljava/lang/String;";
                case VOID -> "V";
            };
        }

        // Fallback
        return "Ljava/lang/Object;";
    }

    private String extractClassName(String typeStr) {
        // Remove common prefixes and suffixes
        typeStr = typeStr.replaceAll("^.*\\.", "")  // Remove package prefixes
                .replaceAll("\\..*$", "")  // Remove suffixes
                .replaceAll("^L", "")      // Remove L prefix
                .replaceAll(";$", "");     // Remove ; suffix

        // Extract the first capitalized word (class name convention)
        String[] parts = typeStr.split("\\s+|\\.|:");
        for (String part : parts) {
            if (part.length() > 0 && Character.isUpperCase(part.charAt(0)) && part.matches("[A-Za-z0-9_]+")) {
                return part;
            }
        }

        return null;
    }

    /**
     * Converts a Java type descriptor back to a readable format (for debugging)
     */
    public String descriptorToReadable(String descriptor) {
        return switch (descriptor) {
            case "I" -> "int";
            case "Z" -> "boolean";
            case "V" -> "void";
            case "Ljava/lang/String;" -> "String";
            case "Ljava/lang/Object;" -> "Object";
            case "[I" -> "int[]";
            case "[Ljava/lang/String;" -> "String[]";
            default -> {
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    yield descriptor.substring(1, descriptor.length() - 1).replace("/", ".");
                } else if (descriptor.startsWith("[L") && descriptor.endsWith(";")) {
                    yield descriptor.substring(2, descriptor.length() - 1).replace("/", ".") + "[]";
                } else {
                    yield descriptor;
                }
            }
        };
    }
}