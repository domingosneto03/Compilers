package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.AccessModifier;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Helpers para instruções Jasmin especializadas: iconst_*, bipush, sipush, iload_*, istore_*, iinc, iflt, etc.
 */
public class JasminUtils {

    // Prefixos “i” ou “a” para int vs. referência
    private static final String[] ILOAD  = {"iload",  "iload_0",  "iload_1",  "iload_2",  "iload_3"};
    private static final String[] ISTORE = {"istore", "istore_0", "istore_1", "istore_2", "istore_3"};
    private static final String[] ALOAD  = {"aload",  "aload_0",  "aload_1",  "aload_2",  "aload_3"};
    private static final String[] ASTORE = {"astore", "astore_0", "astore_1", "astore_2", "astore_3"};

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
    }

    /** “public ”, “private ” ou “” se DEFAULT. */
    public String getModifier(AccessModifier m) {
        return m != AccessModifier.DEFAULT
                ? m.name().toLowerCase() + " "
                : "";
    }

    /** iconst_m1…iconst_5, bipush, sipush ou ldc. */
    public static String getConstInstruction(int v) {
        if (v >= -1 && v <= 5) {
            return v == -1 ? "iconst_m1" : "iconst_" + v;
        }
        if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            return "bipush " + v;
        }
        if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            return "sipush " + v;
        }
        return "ldc " + v;
    }

    /** iload_X ou iload n / aload_X ou aload n. */
    public static String getLoadInstruction(String prefix, int idx) {
        String[] forms = prefix.equals("i") ? ILOAD : ALOAD;
        if (idx >= 0 && idx < forms.length) {
            return forms[idx];
        }
        return forms[0] + " " + idx;
    }

    /** istore_X ou istore n / astore_X ou astore n. */
    public static String getStoreInstruction(String prefix, int idx) {
        String[] forms = prefix.equals("i") ? ISTORE : ASTORE;
        if (idx >= 0 && idx < forms.length) {
            return forms[idx];
        }
        return forms[0] + " " + idx;
    }
}
