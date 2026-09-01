package dev.sweety.transform.transformers.security;

import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Random;

/**
 * Injects opaque predicates (mathematical invariants like x*(x+1) % 2 == 0)
 * into methods to produce bogus control flow, defeating decompilers and symbolic execution.
 */
public final class OpaquePredicateTransformer extends Transformer {

    private final Random random = new Random(1337);

    @Override
    public String name() {
        return "OpaquePredicate";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();
        boolean classCritical = MethodSelector.hasAnnotation(cn.invisibleAnnotations, SecurityCritical.class.getName()) ||
                                MethodSelector.hasAnnotation(cn.visibleAnnotations, SecurityCritical.class.getName());

        for (MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (mn.instructions.size() < 4) continue;

            boolean methodCritical = classCritical ||
                                     MethodSelector.hasAnnotation(mn.invisibleAnnotations, SecurityCritical.class.getName()) ||
                                     MethodSelector.hasAnnotation(mn.visibleAnnotations, SecurityCritical.class.getName());

            if (methodCritical || MethodSelector.shouldTransform(ctx, mn)) {
                injectOpaqueBranch(mn);
            }
        }
    }

    private void injectOpaqueBranch(MethodNode mn) {
        InsnList list = new InsnList();

        LabelNode realBlock = new LabelNode();
        LabelNode trapBlock = new LabelNode();

        // Generate opaque invariant: (x * (x + 1)) & 1 == 0 is ALWAYS TRUE for any int x
        int seed = random.nextInt(1000) + 1;
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false));
        list.add(new InsnNode(Opcodes.L2I)); // int x
        list.add(new InsnNode(Opcodes.DUP)); // x, x
        list.add(new InsnNode(Opcodes.ICONST_1)); // x, x, 1
        list.add(new InsnNode(Opcodes.IADD)); // x, x + 1
        list.add(new InsnNode(Opcodes.IMUL)); // x * (x + 1)
        list.add(new InsnNode(Opcodes.ICONST_1)); // result, 1
        list.add(new InsnNode(Opcodes.IAND)); // (x*(x+1)) & 1
        list.add(new JumpInsnNode(Opcodes.IFEQ, realBlock)); // Always jumps to realBlock!

        // Bogus trap block (never executed at runtime, but confuses decompilers)
        list.add(trapBlock);
        list.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false));
        list.add(new InsnNode(Opcodes.ATHROW));

        // Real block
        list.add(realBlock);

        mn.instructions.insert(list);
    }
}
