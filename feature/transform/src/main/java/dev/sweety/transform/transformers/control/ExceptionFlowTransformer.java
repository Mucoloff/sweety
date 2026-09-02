package dev.sweety.transform.transformers.control;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Exception-Based Control Flow Transformation.
 *
 * Splits a method body into segments separated by manufactured try/catch blocks.
 * Each segment "passes control" to the next via a sentinel throw/catch mechanism.
 */
public final class ExceptionFlowTransformer extends Transformer {

    /** Name of the synthetic exception class injected into the target class. */
    private static final String SENTINEL_SIMPLE = "__F$";

    @Override public String name() { return "ExceptionFlow"; }

    @Override
    public void transform(TransformContext ctx) {
        boolean any = false;
        for (MethodNode mn : ctx.classNode().methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldTransform(ctx, mn)) continue;
            if (!MethodSelector.exceptionFlow(ctx.classNode(), mn)) continue;
            if (mn.instructions.size() < 8) continue;
            if ("<init>".equals(mn.name) || "<clinit>".equals(mn.name)) continue;

            if (splitWithExceptions(ctx.classNode(), mn, ctx.frameClassLoader())) any = true;
        }

        if (any) {
            injectSentinelClass(ctx.classNode());
        }
    }

    private boolean splitWithExceptions(ClassNode cn, MethodNode mn, ClassLoader frameClassLoader) {
        final String sentinelInternal = cn.name + "$" + SENTINEL_SIMPLE;
        final InsnList insns = mn.instructions;

        final InsnList backup = cloneInsnList(insns);
        final List<TryCatchBlockNode> tcBackup = mn.tryCatchBlocks == null
                ? new ArrayList<>()
                : new ArrayList<>(mn.tryCatchBlocks);

        int splitPoint = findSafeSplitPoint(mn);
        if (splitPoint < 0) return false;

        final AbstractInsnNode splitInsn = insns.get(splitPoint);

        final LabelNode tryStart     = new LabelNode();
        final LabelNode tryEnd       = new LabelNode();
        final LabelNode catchHandler = new LabelNode();

        final InsnList throwBlock = new InsnList();
        throwBlock.add(new TypeInsnNode(Opcodes.NEW, sentinelInternal));
        throwBlock.add(new InsnNode(Opcodes.DUP));
        throwBlock.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, sentinelInternal, "<init>", "()V", false));
        throwBlock.add(new InsnNode(Opcodes.ATHROW));

        final AbstractInsnNode firstReal = findFirstReal(insns);
        if (firstReal == null) return false;
        insns.insertBefore(firstReal, tryStart);

        insns.insert(splitInsn, throwBlock);
        insns.insert(throwBlock.getLast(), tryEnd);

        final InsnList catchPrologue = new InsnList();
        catchPrologue.add(catchHandler);
        catchPrologue.add(new InsnNode(Opcodes.POP));

        final AbstractInsnNode resumePoint = tryEnd.getNext();
        if (resumePoint != null) {
            insns.insertBefore(resumePoint, catchPrologue);
        } else {
            insns.add(catchPrologue);
        }

        if (mn.tryCatchBlocks == null) mn.tryCatchBlocks = new ArrayList<>();
        mn.tryCatchBlocks.add(0, new TryCatchBlockNode(tryStart, tryEnd, catchHandler, sentinelInternal));

        if (!verifySoundness(cn, mn, frameClassLoader)) {
            mn.instructions = backup;
            mn.tryCatchBlocks = tcBackup;
            return false;
        }

        return true;
    }

    private static int findSafeSplitPoint(MethodNode mn) {
        final InsnList list = mn.instructions;
        final int total = list.size();
        final int target = total / 2;

        for (int i = target; i < total - 3; i++) {
            final AbstractInsnNode insn = list.get(i);
            if (isSafeSplitInstruction(insn)) return i;
        }
        for (int i = target - 1; i > 2; i--) {
            final AbstractInsnNode insn = list.get(i);
            if (isSafeSplitInstruction(insn)) return i;
        }
        return -1;
    }

    private static boolean isSafeSplitInstruction(AbstractInsnNode insn) {
        final int op = insn.getOpcode();
        if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) return true;
        if (op == Opcodes.POP || op == Opcodes.POP2) return true;
        if (op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC) return true;
        return false;
    }

    private static AbstractInsnNode findFirstReal(InsnList list) {
        for (AbstractInsnNode insn : list) {
            if (!(insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode)) {
                return insn;
            }
        }
        return null;
    }

    private static void injectSentinelClass(ClassNode cn) {
        final String innerInternal = cn.name + "$" + SENTINEL_SIMPLE;
        if (cn.innerClasses == null) cn.innerClasses = new ArrayList<>();
        for (InnerClassNode icn : cn.innerClasses) {
            if (innerInternal.equals(icn.name)) return;
        }
        cn.innerClasses.add(new InnerClassNode(
                innerInternal,
                cn.name,
                SENTINEL_SIMPLE,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC));
    }

    public static byte[] buildSentinelClass(String outerInternal) {
        final String innerInternal = outerInternal + "$" + SENTINEL_SIMPLE;
        final ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
                innerInternal,
                null,
                "java/lang/RuntimeException",
                null);
        cw.visitInnerClass(
                innerInternal,
                outerInternal,
                SENTINEL_SIMPLE,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC);

        final MethodVisitor init = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/RuntimeException",
                "<init>",
                "(Ljava/lang/String;Ljava/lang/Throwable;ZZ)V",
                false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(5, 1);
        init.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static boolean verifySoundness(ClassNode cn, MethodNode mn, ClassLoader frameClassLoader) {
        try {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    final ClassLoader cl = (frameClassLoader != null) ? frameClassLoader : getClass().getClassLoader();
                    Class<?> c, d;
                    try {
                        c = Class.forName(type1.replace('/', '.'), false, cl);
                        d = Class.forName(type2.replace('/', '.'), false, cl);
                    } catch (Exception e) {
                        return "java/lang/Object";
                    }
                    if (c.isAssignableFrom(d)) return type1;
                    if (d.isAssignableFrom(c)) return type2;
                    if (c.isInterface() || d.isInterface()) return "java/lang/Object";
                    do {
                        c = c.getSuperclass();
                    } while (!c.isAssignableFrom(d));
                    return c.getName().replace('.', '/');
                }
            };
            cn.accept(cw);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static InsnList cloneInsnList(InsnList original) {
        final InsnList clone = new InsnList();
        final Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode insn : original.toArray()) {
            if (insn instanceof LabelNode ln) {
                labelMap.put(ln, new LabelNode());
            }
        }
        for (AbstractInsnNode insn : original.toArray()) {
            clone.add(insn.clone(labelMap));
        }
        return clone;
    }
}
