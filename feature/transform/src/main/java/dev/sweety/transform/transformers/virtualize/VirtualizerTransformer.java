package dev.sweety.transform.transformers.virtualize;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import dev.sweety.transform.transformers.constant.IntegerEncodingTransformer;
import dev.sweety.util.logger.SimpleLogger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Virtualizer Transformer.
 * <p>
 * For each method annotated with {@code @Virtualize}:
 * <ol>
 *   <li>Compile the method body to VM bytecode via {@link VMCompiler}.</li>
 *   <li>Store the bytecode as a {@code private static final byte[]} field with an
 *       obfuscated name, initialised in {@code <clinit>}.</li>
 *   <li>Replace the method body with a stub that delegates to
 *       {@code VMInterpreter.execute(this_or_null, args, bytecode)}.</li>
 *   <li>Strip the {@code @Virtualize} annotation.</li>
 * </ol>
 *
 * Stub shape (instance method example):
 * <pre>
 *   Object __r = VMInterpreter.execute(this, new Object[]{arg0, arg1}, __vm$methodName);
 *   return (ReturnType) __r;  // cast + unbox as appropriate
 * </pre>
 *
 * <h3>Field naming</h3>
 * The bytecode field is named {@code __vm$} + hex(FNV32(methodName+desc)) to avoid
 * collisions with multiple virtualized methods in the same class.
 */
public final class VirtualizerTransformer extends Transformer {

    private static final SimpleLogger logger = SimpleLogger.of(VirtualizerTransformer.class);

    private static final String VM_INTERPRETER = "dev/sweety/transform/vm/core/VMInterpreter";
    private static final String EXECUTE_DESC =
            "(Ljava/lang/Object;[Ljava/lang/Object;[B)Ljava/lang/Object;";

    /** Eager (plaintext, baked at transform time) vs gated (field left unset — see
     *  {@link VirtualBytecodeService}, filled in only after the client proves a live session). */
    private final boolean gated;

    public VirtualizerTransformer() { this(false); }

    public VirtualizerTransformer(boolean gated) { this.gated = gated; }

    @Override public String name() { return "Virtualizer"; }

    @Override
    public void transform(TransformContext ctx) {
        final ClassNode cn = ctx.classNode();
        final List<MethodNode> toVirtualize = new ArrayList<>();

        for (MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldVirtualize(mn)) continue;
            if (!MethodSelector.isVirtualizable(mn)) continue;
            toVirtualize.add(mn);
        }

        for (MethodNode mn : toVirtualize) {
            try {
                virtualize(cn, mn);
                ctx.markProcessed(mn);
                MethodSelector.stripAnnotations(mn);
            } catch (Exception e) {
                logger.profile("virtualize").warn("Skipping " + cn.name + "." + mn.name + mn.desc
                        + " — compile error: " + e.getMessage());
            }
        }
    }

    // ── Core virtualization ───────────────────────────────────────────────────

    private void virtualize(ClassNode cn, MethodNode mn) {
        final String fieldName = fieldNameFor(mn);

        if (gated) {
            // Field left unset (null) — VirtualBytecodeService fills it in on demand, only after the
            // client proves a live session. Not final: it's reflectively set well after <clinit>.
            declareBytecodeField(cn, fieldName, false);
        } else {
            // 1. Compile to VM bytecode
            final byte[] vmCode = VMCompiler.compile(cn.name, mn);
            // 2. Create storage field + bake it in eagerly
            injectBytecodeField(cn, fieldName, vmCode);
        }

        // 3. Replace method body with stub (same in both modes — reads whatever the field holds)
        replaceWithStub(cn, mn, fieldName);
    }

    /**
     * The injected static field name for a virtualized method — same derivation used at initial
     * transform time and by {@link VirtualBytecodeService} when recompiling on demand, so the two
     * always agree on which field a given method's bytecode belongs in.
     */
    public static String fieldNameFor(MethodNode mn) {
        return "__vm$" + fnv32hex(mn.name + mn.desc);
    }

    // ── Bytecode field injection ──────────────────────────────────────────────

    /** Declares the storage field with no initializer — used by gated mode; filled in later via
     *  reflection by the client once it decrypts the on-demand payload (see {@link VirtualBytecodeService}). */
    private static void declareBytecodeField(ClassNode cn, String fieldName, boolean isFinal) {
        int access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        if (isFinal) access |= Opcodes.ACC_FINAL;
        cn.fields.add(new FieldNode(access, fieldName, "[B", null, null));
    }

    private static void injectBytecodeField(ClassNode cn, String fieldName, byte[] vmCode) {
        // Add private static final byte[] field
        final FieldNode field = new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                fieldName, "[B", null, null);
        cn.fields.add(field);

        // Inject static initializer that builds the array
        MethodNode clinit = findOrCreateClinit(cn);

        // Build the byte[] initialization code and inject it BEFORE the existing RETURN
        final InsnList init = buildArrayInit(vmCode, cn.name, fieldName);
        // Find the RETURN in clinit and insert before it
        AbstractInsnNode returnInsn = null;
        for (AbstractInsnNode insn : clinit.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) { returnInsn = insn; break; }
        }
        if (returnInsn != null) {
            clinit.instructions.insertBefore(returnInsn, init);
        } else {
            clinit.instructions.add(init);
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        }
    }

    /**
     * Generates instructions that push each byte of {@code vmCode} and store the
     * array into the static field.  For large byte arrays we use a helper approach
     * to stay within method size limits.
     */
    private static InsnList buildArrayInit(byte[] vmCode, String owner, String fieldName) {
        final InsnList list = new InsnList();

        // new byte[vmCode.length]
        list.add(new LdcInsnNode(vmCode.length));
        list.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE));

        for (int i = 0; i < vmCode.length; i++) {
            list.add(new InsnNode(Opcodes.DUP));
            list.add(pushInt(i));
            list.add(pushInt(vmCode[i] & 0xFF)); // unsigned
            // Re-sign: push the signed byte value
            list.add(new InsnNode(Opcodes.I2B));
            list.add(new InsnNode(Opcodes.BASTORE));
        }

        list.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, fieldName, "[B"));
        return list;
    }

    // ── Stub generation ───────────────────────────────────────────────────────

    private static void replaceWithStub(ClassNode cn, MethodNode mn, String fieldName) {
        final boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
        final Type returnType  = Type.getReturnType(mn.desc);
        final Type[] argTypes  = Type.getArgumentTypes(mn.desc);

        mn.instructions.clear();
        mn.tryCatchBlocks.clear();
        mn.localVariables = null;

        final InsnList stub = new InsnList();

        // Arg 1: this (or null for static)
        if (isStatic) {
            stub.add(new InsnNode(Opcodes.ACONST_NULL));
        } else {
            stub.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }

        // Arg 2: Object[] args
        stub.add(pushInt(argTypes.length));
        stub.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

        int localIdx = isStatic ? 0 : 1;
        for (int i = 0; i < argTypes.length; i++) {
            stub.add(new InsnNode(Opcodes.DUP));
            stub.add(pushInt(i));
            loadAndBox(stub, argTypes[i], localIdx);
            stub.add(new InsnNode(Opcodes.AASTORE));
            localIdx += argTypes[i].getSize();
        }

        // Arg 3: bytecode field
        stub.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, fieldName, "[B"));

        // VMInterpreter.execute(...)
        stub.add(new MethodInsnNode(Opcodes.INVOKESTATIC, VM_INTERPRETER, "execute",
                EXECUTE_DESC, false));

        // Handle return type
        emitReturn(stub, returnType);

        mn.instructions.add(stub);
        mn.maxStack  = 8;
        mn.maxLocals = localIdx + 4;
    }

    // ── Return type handling ──────────────────────────────────────────────────

    private static void emitReturn(InsnList stub, Type returnType) {
        switch (returnType.getSort()) {
            case Type.VOID -> {
                stub.add(new InsnNode(Opcodes.POP));
                stub.add(new InsnNode(Opcodes.RETURN));
            }
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> {
                stub.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                stub.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                        "intValue", "()I", false));
                stub.add(new InsnNode(Opcodes.IRETURN));
            }
            case Type.LONG -> {
                stub.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                stub.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                        "longValue", "()J", false));
                stub.add(new InsnNode(Opcodes.LRETURN));
            }
            case Type.FLOAT -> {
                stub.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                stub.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                        "floatValue", "()F", false));
                stub.add(new InsnNode(Opcodes.FRETURN));
            }
            case Type.DOUBLE -> {
                stub.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                stub.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                        "doubleValue", "()D", false));
                stub.add(new InsnNode(Opcodes.DRETURN));
            }
            default -> {
                if (!returnType.getInternalName().equals("java/lang/Object")) {
                    stub.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
                }
                stub.add(new InsnNode(Opcodes.ARETURN));
            }
        }
    }

    // ── Boxing helpers ────────────────────────────────────────────────────────

    private static void loadAndBox(InsnList list, Type type, int localIdx) {
        switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> {
                list.add(new VarInsnNode(Opcodes.ILOAD, localIdx));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer",
                        "valueOf", "(I)Ljava/lang/Integer;", false));
            }
            case Type.LONG -> {
                list.add(new VarInsnNode(Opcodes.LLOAD, localIdx));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long",
                        "valueOf", "(J)Ljava/lang/Long;", false));
            }
            case Type.FLOAT -> {
                list.add(new VarInsnNode(Opcodes.FLOAD, localIdx));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float",
                        "valueOf", "(F)Ljava/lang/Float;", false));
            }
            case Type.DOUBLE -> {
                list.add(new VarInsnNode(Opcodes.DLOAD, localIdx));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double",
                        "valueOf", "(D)Ljava/lang/Double;", false));
            }
            default -> list.add(new VarInsnNode(Opcodes.ALOAD, localIdx));
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static MethodNode findOrCreateClinit(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if ("<clinit>".equals(m.name)) return m;
        }
        final MethodNode clinit = new MethodNode(
                Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(clinit);
        return clinit;
    }

    private static AbstractInsnNode pushInt(int v) {
        return IntegerEncodingTransformer.pushInt(v);
    }

    /** FNV-32 of string → 8-char lowercase hex for unique field names. */
    private static String fnv32hex(String s) {
        int hash = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b;
            hash *= 0x01000193;
        }
        return Integer.toHexString(hash >>> 0);
    }
}
