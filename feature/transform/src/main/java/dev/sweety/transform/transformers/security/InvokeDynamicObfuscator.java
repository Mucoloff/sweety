package dev.sweety.transform.transformers.security;

import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Replaces static method calls with invokedynamic instructions targeting IndyBootstrap.
 */
public final class InvokeDynamicObfuscator extends Transformer {

    @Override
    public String name() {
        return "InvokeDynamic";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();

        Handle bsmHandle = new Handle(
                Opcodes.H_INVOKESTATIC,
                "dev/sweety/transform/transformers/security/IndyBootstrap",
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/invoke/CallSite;",
                false
        );

        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    if (minsn.owner.equals(cn.name)) continue; // Keep local methods as-is

                    // Replace with invokedynamic
                    InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                            minsn.name,
                            minsn.desc,
                            bsmHandle,
                            minsn.owner,
                            minsn.name,
                            minsn.desc
                    );
                    mn.instructions.set(insn, indy);
                }
            }
        }
    }
}
