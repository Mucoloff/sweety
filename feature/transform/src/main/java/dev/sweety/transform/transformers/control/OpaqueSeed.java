package dev.sweety.transform.transformers.control;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Shared per-class opaque seed: a NON-final {@code private static int} field, set once in
 * {@code <clinit>} to a bake-time constant. Because the field is not {@code final} and not a
 * compile-time constant, a generic constant-folding deobfuscator (e.g. narumi's
 * {@code UniversalNumber}/{@code UniversalFlow}) cannot value-track it, so any expression built on
 * top of it — encoded integer constants, opaque branch predicates — survives its cleaning pass.
 * At runtime the field never changes, so the JIT folds it back to a constant → ~zero cost.
 */
public final class OpaqueSeed {

    private OpaqueSeed() {}

    private static final long BUILD_SEED =
            Long.parseLong(System.getProperty("sweety.transform.seed", String.valueOf(0x5EEDBEEFL)));

    /** Deterministic per-class seed value. */
    public static int seed(String internalName) {
        int h = 0x811c9dc5;
        for (int i = 0; i < internalName.length(); i++) { h ^= internalName.charAt(i); h *= 0x01000193; }
        int s = (int) (h ^ BUILD_SEED);
        return s == 0 ? 0x51ED : s;
    }

    /** Synthetic-looking per-class field name holding the seed. */
    public static String fieldName(String internalName) {
        int x = seed(internalName) ^ 0x77c1;
        final char[] c = new char[7];
        for (int i = 0; i < 7; i++) { c[i] = (char) ('a' + Math.floorMod(x, 26)); x = x * 31 + 3; }
        return new String(c);
    }

    /** {@code GETSTATIC <owner>.<seedField> : I}. */
    public static FieldInsnNode get(ClassNode cn) {
        return new FieldInsnNode(Opcodes.GETSTATIC, cn.name, fieldName(cn.name), "I");
    }

    /**
     * Ensures the seed field + its {@code <clinit>} initialiser exist (idempotent).
     */
    public static String ensureField(ClassNode cn) {
        final String fn = fieldName(cn.name);
        for (FieldNode f : cn.fields) if (fn.equals(f.name)) return fn; // already present

        cn.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, fn, "I", null, null));

        final InsnList init = new InsnList();
        init.add(pushInt(seed(cn.name)));
        init.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, fn, "I"));

        MethodNode clinit = null;
        for (MethodNode m : cn.methods) if ("<clinit>".equals(m.name)) { clinit = m; break; }
        if (clinit == null) {
            clinit = new MethodNode(
                    Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(clinit);
        }
        clinit.instructions.insert(init);
        return fn;
    }

    private static AbstractInsnNode pushInt(int v) {
        if (v >= -1 && v <= 5) return new InsnNode(Opcodes.ICONST_0 + v);
        if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) return new IntInsnNode(Opcodes.BIPUSH, v);
        if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) return new IntInsnNode(Opcodes.SIPUSH, v);
        return new LdcInsnNode(v);
    }
}
