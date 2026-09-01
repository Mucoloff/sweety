package dev.sweety.transform.transformers.control;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Conditional Expression Mutation via De Morgan's Law &amp; equivalence rewriting.
 *
 * For each conditional jump instruction, replaces it with a logically equivalent
 * but structurally different sequence that confuses decompiler control-flow recovery.
 *
 * Examples:
 * <pre>
 *   IFEQ L            →  IFNE L_neg; GOTO L; L_neg:
 *   IF_ICMPGT L        →  IF_ICMPLE L_neg; GOTO L; L_neg:
 *   IFNULL L           →  IFNONNULL L_neg; GOTO L; L_neg:
 * </pre>
 *
 * Effect: every if-statement appears to branch "backwards" — tools that pattern-match
 * IFEQ → "if (x == 0)" now see IFNE pointing to the else-block, fragmenting their
 * control-flow graph reconstruction.
 *
 * Runtime cost: one extra GOTO per conditional (JIT eliminates after first run).
 */
public final class ConditionalMutationTransformer extends Transformer {

    @Override public String name() { return "ConditionalMutation"; }

    @Override
    public void transform(TransformContext ctx) {
        for (MethodNode mn : ctx.classNode().methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldTransform(ctx, mn)) continue;

            mutateConditionals(mn);
        }
    }

    private void mutateConditionals(MethodNode mn) {
        final InsnList insns = mn.instructions;

        for (AbstractInsnNode insn : insns.toArray()) {
            final int op = insn.getOpcode();
            final int negated = negate(op);
            if (negated == -1) continue;

            final JumpInsnNode original = (JumpInsnNode) insn;
            final LabelNode    trueTarget = original.label;
            final LabelNode    fallThrough = new LabelNode();

            final InsnList replacement = new InsnList();

            // Negated condition → jump to fallthrough (= original false path)
            replacement.add(new JumpInsnNode(negated, fallThrough));
            // Taken path: GOTO original target
            replacement.add(new JumpInsnNode(Opcodes.GOTO, trueTarget));
            replacement.add(fallThrough);
            // Execution continues here if negated condition was true (= original false)

            insns.insertBefore(insn, replacement);
            insns.remove(insn);
        }
    }

    /** Returns the semantically negated opcode, or -1 if the instruction is not a supported conditional jump. */
    private static int negate(int op) {
        return switch (op) {
            case Opcodes.IFEQ      -> Opcodes.IFNE;
            case Opcodes.IFNE      -> Opcodes.IFEQ;
            case Opcodes.IFLT      -> Opcodes.IFGE;
            case Opcodes.IFGE      -> Opcodes.IFLT;
            case Opcodes.IFGT      -> Opcodes.IFLE;
            case Opcodes.IFLE      -> Opcodes.IFGT;
            case Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE;
            case Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ;
            case Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE;
            case Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT;
            case Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE;
            case Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT;
            case Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE;
            case Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ;
            case Opcodes.IFNULL    -> Opcodes.IFNONNULL;
            case Opcodes.IFNONNULL -> Opcodes.IFNULL;
            default                -> -1;
        };
    }
}
