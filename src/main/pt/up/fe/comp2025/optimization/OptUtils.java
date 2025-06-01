package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;
import static pt.up.fe.comp2025.ast.Kind.TYPE;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {

    private final AccumulatorMap<String> temporaries;
    private final Map<String, Integer> nestingLevels;

    private final TypeUtils types;

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
        this.nestingLevels = new HashMap<>();
    }

    public String nextTemp() {
        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {
        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;
        return prefix + nextTempNum;
    }
    
    /**
     * Generates a unique label for control flow structures, taking nesting into account.
     * For control flow labels like loop/if/else, we want to ensure they're unique
     * across different nesting levels.
     * 
     * @param basePrefix The base prefix for the label (loop, then, else, endif, etc.)
     * @return A unique label string
     */
    public String nextControlFlowLabel(String basePrefix) {
        int nestLevel = nestingLevels.getOrDefault(basePrefix, 0);
        nestingLevels.put(basePrefix, nestLevel + 1);
        
        // Use both nesting level and counter to ensure uniqueness
        int counter = temporaries.add(basePrefix) - 1;
        return basePrefix + "_" + nestLevel + "_" + counter;
    }

    public String toOllirType(JmmNode typeNode) {
        TYPE.checkOrThrow(typeNode);
        return toOllirType(types.convertType(typeNode));
    }

    public static String toOllirType(Type type) {
        // Add null check to prevent NullPointerException
        if (type == null) {
            // Default to void when type is null
            return ".V";
        }

        String result = "";
        if (type.isArray()) {
            result += ".array";
        }
        return result + toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {
        // Add null check for type name as well
        if (typeName == null) {
            return ".V"; // Default to void for null type name
        }

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "String" -> "String";
            case "void" -> "V";
            default -> typeName; // Use the type name directly instead of throwing exception
        };

        return type;
    }
}
