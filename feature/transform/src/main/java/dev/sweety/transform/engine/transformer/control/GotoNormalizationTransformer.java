package dev.sweety.transform.engine.transformer.control;

import dev.sweety.transform.engine.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * GOTO Normalization &amp; Opaque Jump Rewriter.
 *
 * Two-phase pass:
 * <ol>
 *   <li><strong>Flatten goto chains</strong> — if a GOTO lands on another GOTO,
 *       redirect to the final target. O(n) per method.</li>
 *   <li><strong>Opaque jump insertion</strong> — for every unconditional GOTO,
 *       replace it with a conditional that always branches the same way using
 *       a mathematically opaque predicate.  Example:
 *       <pre>
 *         GOTO L  →  ICONST_1; IFEQ L_never; GOTO L; L_never: ATHROW
 *       </pre>
 *       This confuses decompilers that try to recover for-loop structure.
 *   </li>
 * </ol>
 *
 * Performance: zero overhead at runtime — the opaque predicates are constant-foldable
 * by the JIT after first invocation.
 */
public final class GotoNormalizationTransformer extends Transformer {

    @Override public String name() { return "GotoNormalization"; }

    @Override
    public void transform(TransformContext ctx) {
        for (MethodNode mn : ctx.classNode().methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldTransform(ctx, mn)) continue;

            flattenGotoChains(mn);
            insertOpaqueJumps(mn);

            MethodSelector.stripAnnotations(mn);
        }
    }

    // ── Phase 1: Flatten GOTO chains ──────────────────────────────────────────

    private void flattenGotoChains(MethodNode mn) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.GOTO) continue;
                final JumpInsnNode jump = (JumpInsnNode) insn;

                // Walk past labels at the target to find the real instruction
                AbstractInsnNode target = jump.label;
                while ((target instanceof LabelNode || target instanceof LineNumberNode)) {
                    target = target.getNext();
                }

                if (target != null && target.getOpcode() == Opcodes.GOTO) {
                    // Chain: redirect to the final destination
                    jump.label = ((JumpInsnNode) target).label;
                    changed = true;
                }
            }
        }
    }

    // ── Phase 2: Opaque jump insertion ────────────────────────────────────────

    /**
     * Replaces GOTO L with:
     *   ICONST_1       ← always 1
     *   IFNE L         ← always taken (1 != 0)
     *   // dead block: ICONST_0; IFEQ L; GOTO L (unreachable)
     *
     * Decompilers see a conditional branch and cannot reliably determine it's unconditional.
     */
    private void insertOpaqueJumps(MethodNode mn) {
        final InsnList insns = mn.instructions;

        for (AbstractInsnNode insn : insns.toArray()) {
            if (insn.getOpcode() != Opcodes.GOTO) continue;
            final JumpInsnNode gotoInsn = (JumpInsnNode) insn;
            final LabelNode trueTarget  = gotoInsn.label;

            // Dead label (never reached)
            final LabelNode deadLabel = new LabelNode();

            final InsnList replacement = new InsnList();
            // Opaque predicate: (1 | 0) != 0 — always true
            replacement.add(new InsnNode(Opcodes.ICONST_1));
            replacement.add(new InsnNode(Opcodes.ICONST_0));
            replacement.add(new InsnNode(Opcodes.IOR));
            replacement.add(new JumpInsnNode(Opcodes.IFNE, trueTarget));  // always taken
            // Dead block below — unreachable at runtime but visible in bytecode
            replacement.add(deadLabel);
            replacement.add(new InsnNode(Opcodes.ACONST_NULL));
            replacement.add(new InsnNode(Opcodes.ATHROW)); // keeps verifier happy (stack depth = 1)

            insns.insertBefore(insn, replacement);
            insns.remove(insn);
        }
    }
}
