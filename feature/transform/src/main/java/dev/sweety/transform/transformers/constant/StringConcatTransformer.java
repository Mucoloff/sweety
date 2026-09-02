package dev.sweety.transform.transformers.constant;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * invokedynamic string-concat desugaring.
 *
 * Java compiles {@code "a" + x + "b"} into an {@code invokedynamic makeConcatWithConstants}
 * whose literal text lives in the bootstrap-method recipe / constant args — NOT as
 * {@code LDC} strings. A plain LDC-only string encryptor therefore misses every concatenation
 * literal. This pass rewrites such call sites into an explicit {@code StringBuilder} chain
 * where each literal becomes an {@code LDC String}, so the following {@link StringEncryptionTransformer}
 * pass encrypts them like any other constant.
 */
public final class StringConcatTransformer extends Transformer {

    private static final String SCF = "java/lang/invoke/StringConcatFactory";
    private static final char ARG = '\u0001';  // recipe: next dynamic arg
    private static final char CST = '\u0002';  // recipe: next constant arg

    @Override
    public String name() {
        return "StringConcat";
    }

    @Override
    public void transform(TransformContext ctx) {
        final ClassNode cn = ctx.classNode();
        for (MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            desugar(mn);
        }
    }

    private void desugar(MethodNode mn) {
        int nextLocal = mn.maxLocals;
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (!(insn instanceof InvokeDynamicInsnNode indy)) continue;
            if (!"makeConcatWithConstants".equals(indy.name)) continue;
            if (indy.bsm == null || !SCF.equals(indy.bsm.getOwner())) continue;
            if (indy.bsmArgs == null || indy.bsmArgs.length < 1) continue;
            if (!(indy.bsmArgs[0] instanceof String recipe)) continue;

            boolean ok = true;
            for (int i = 1; i < indy.bsmArgs.length; i++) {
                if (!(indy.bsmArgs[i] instanceof String)) { ok = false; break; }
            }
            if (!ok) continue;

            final Type[] argTypes = Type.getArgumentTypes(indy.desc);
            final int[] locals = new int[argTypes.length];
            for (int i = 0; i < argTypes.length; i++) {
                locals[i] = nextLocal;
                nextLocal += argTypes[i].getSize();
            }

            final InsnList list = new InsnList();

            // 1. Pop dynamic args off operand stack into temporary locals (reverse order)
            for (int i = argTypes.length - 1; i >= 0; i--) {
                list.add(new VarInsnNode(argTypes[i].getOpcode(Opcodes.ISTORE), locals[i]));
            }

            // 2. new StringBuilder()
            list.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
            list.add(new InsnNode(Opcodes.DUP));
            list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));

            // 3. Replay recipe: append literals (LDC), dynamic args (ILOAD+append), and constant args
            int dynIdx = 0;
            int cstIdx = 1;
            final StringBuilder literalBuf = new StringBuilder();

            for (int i = 0; i < recipe.length(); i++) {
                final char c = recipe.charAt(i);
                if (c == ARG) {
                    flushLiteral(list, literalBuf);
                    if (dynIdx < argTypes.length) {
                        final Type t = argTypes[dynIdx];
                        list.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), locals[dynIdx]));
                        appendDynamic(list, t);
                        dynIdx++;
                    }
                } else if (c == CST) {
                    flushLiteral(list, literalBuf);
                    if (cstIdx < indy.bsmArgs.length) {
                        list.add(new LdcInsnNode(indy.bsmArgs[cstIdx]));
                        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                                "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
                        cstIdx++;
                    }
                } else {
                    literalBuf.append(c);
                }
            }
            flushLiteral(list, literalBuf);

            // 4. .toString()
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "toString", "()Ljava/lang/String;", false));

            mn.instructions.insertBefore(indy, list);
            mn.instructions.remove(indy);
        }
        mn.maxLocals = Math.max(mn.maxLocals, nextLocal);
    }

    private static void flushLiteral(InsnList list, StringBuilder buf) {
        if (buf.length() == 0) return;
        list.add(new LdcInsnNode(buf.toString()));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        buf.setLength(0);
    }

    private static void appendDynamic(InsnList list, Type t) {
        final String paramDesc;
        switch (t.getSort()) {
            case Type.BOOLEAN -> paramDesc = "(Z)Ljava/lang/StringBuilder;";
            case Type.CHAR    -> paramDesc = "(C)Ljava/lang/StringBuilder;";
            case Type.BYTE, Type.SHORT, Type.INT -> paramDesc = "(I)Ljava/lang/StringBuilder;";
            case Type.LONG    -> paramDesc = "(J)Ljava/lang/StringBuilder;";
            case Type.FLOAT   -> paramDesc = "(F)Ljava/lang/StringBuilder;";
            case Type.DOUBLE  -> paramDesc = "(D)Ljava/lang/StringBuilder;";
            default -> {
                if ("Ljava/lang/String;".equals(t.getDescriptor())) {
                    paramDesc = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
                } else {
                    paramDesc = "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
                }
            }
        }
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", paramDesc, false));
    }
}
