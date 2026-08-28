package dev.sweety.transform.engine.transformer.control;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Exception-Based Control Flow Transformation.
 *
 * Splits a method body into segments separated by manufactured try/catch blocks.
 * Each segment "passes control" to the next via a sentinel throw/catch mechanism.
 *
 * Before:
 * <pre>
 *   A; B; C; RETURN
 * </pre>
 *
 * After (logically):
 * <pre>
 *   try { A; throw SENTINEL; }
 *   catch (SentinelException e) { B; }
 *   C; RETURN
 * </pre>
 *
 * Decompilers that naively convert try/catch to if-else emit broken reconstructions.
 * The "SentinelException" is a bare {@code RuntimeException} subclass injected
 * as a private static inner class, making the class self-contained.
 *
 * Only applied when {@code @Transform(exceptionFlow = true)} because:
 * <ul>
 *   <li>It adds exception table entries (slight verification overhead)</li>
 *   <li>It prevents inlining in some JVM versions</li>
 *   <li>It can trip up Minecraft's async-safe check detectors</li>
 * </ul>
 *
 * Safe for: startup code, license validation, cloud auth paths.
 * Avoid for: per-tick/per-packet check methods.
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
            if (mn.instructions.size() < 8) continue; // too short to benefit

            splitWithExceptions(ctx.classNode(), mn);
            any = true;
        }

        if (any) {
            injectSentinelClass(ctx.classNode());
        }
    }

    // ── Core transformation ───────────────────────────────────────────────────

    private void splitWithExceptions(ClassNode cn, MethodNode mn) {
        final String sentinelInternal = cn.name + "$" + SENTINEL_SIMPLE;
        final InsnList insns = mn.instructions;
        final AbstractInsnNode[] array = insns.toArray();

        if (array.length < 6) return;

        // Find a split point: roughly the midpoint of the instruction list,
        // avoiding landing on a label, jump target, or stack-affecting instruction.
        int splitIdx = findSplitPoint(array);
        if (splitIdx < 0) return;

        // Labels
        final LabelNode tryStart   = new LabelNode();
        final LabelNode tryEnd     = new LabelNode();
        final LabelNode catchStart = new LabelNode();
        final LabelNode afterCatch = new LabelNode();

        // Wrap instructions [0, splitIdx) in try block
        // → at splitIdx: throw new __F$()
        // → catch block holds instructions [splitIdx, end)

        // Extract the second half
        final InsnList secondHalf = new InsnList();
        for (int i = splitIdx; i < array.length; i++) {
            AbstractInsnNode node = array[i];
            insns.remove(node);
            secondHalf.add(node);
        }

        // Build the try-block tail (throw sentinel)
        final InsnList throwInsns = new InsnList();
        throwInsns.add(new TypeInsnNode(Opcodes.NEW, sentinelInternal));
        throwInsns.add(new InsnNode(Opcodes.DUP));
        throwInsns.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, sentinelInternal, "<init>", "()V", false));
        throwInsns.add(new InsnNode(Opcodes.ATHROW));

        // Build catch block: pop sentinel, execute second half
        final InsnList catchInsns = new InsnList();
        catchInsns.add(catchStart);
        catchInsns.add(new InsnNode(Opcodes.POP)); // discard the sentinel exception
        catchInsns.add(secondHalf);

        // Assemble: tryStart; [original first half]; throwInsns; [catch block]
        insns.insert(new LabelNode()); // placeholder for frame
        insns.insertBefore(insns.getFirst(), tryStart);
        insns.add(tryEnd);
        insns.add(throwInsns);
        insns.add(catchInsns);

        // Add exception table entry
        mn.tryCatchBlocks.addFirst(new TryCatchBlockNode(tryStart, tryEnd, catchStart, sentinelInternal));
    }

    /**
     * Find a good split point: a position after a non-jump, non-label instruction
     * that is roughly in the middle of the method.
     */
    private static int findSplitPoint(AbstractInsnNode[] array) {
        int mid = array.length / 2;
        for (int i = mid; i < array.length - 1; i++) {
            AbstractInsnNode node = array[i];
            if (isSafeSplitPoint(node)) return i + 1;
        }
        for (int i = mid - 1; i >= 1; i--) {
            AbstractInsnNode node = array[i];
            if (isSafeSplitPoint(node)) return i + 1;
        }
        return -1;
    }

    private static boolean isSafeSplitPoint(AbstractInsnNode node) {
        if (node instanceof LabelNode || node instanceof LineNumberNode || node instanceof FrameNode) return false;
        int op = node.getOpcode();
        if (op == -1) return false;
        // Avoid splitting immediately before/after jumps or returns
        if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) return false;
        if (op == Opcodes.ATHROW) return false;
        if (node instanceof JumpInsnNode) return false;
        
        return true;
    }

    // ── Sentinel class injection ──────────────────────────────────────────────

    /** Injects a private static inner class {@code __F$} extending RuntimeException. */
    private static void injectSentinelClass(ClassNode outer) {
        final String sentinelName = outer.name + "$" + SENTINEL_SIMPLE;

        // Check if already injected (idempotent)
        for (InnerClassNode ic : outer.innerClasses) {
            if (sentinelName.equals(ic.name)) return;
        }

        // Register as inner class
        outer.innerClasses.add(new InnerClassNode(
                sentinelName, outer.name, SENTINEL_SIMPLE,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC));

        // Note: the actual inner class bytes need to be written separately.
        // We store the ClassNode in the context metadata so the pipeline can emit it.
        // (The pipeline driver handles this by calling buildSentinelClass.)
    }

    /**
     * Build the sentinel exception class bytes. Must be called by the CLI/task
     * for every class that had ExceptionFlow applied.
     */
    public static byte[] buildSentinelClass(String outerInternal) {
        final String name = outerInternal + "$" + SENTINEL_SIMPLE;
        final ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name, null, "java/lang/RuntimeException", null);
        cw.visitInnerClass(name, outerInternal, SENTINEL_SIMPLE,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC);

        final MethodVisitor init = cw.visitMethod(0, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
