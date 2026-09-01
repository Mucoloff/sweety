package dev.sweety.transform.transformers.security;

import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Injects runtime anti-tamper, debugger detection, and JVM TI / agent dump guards into security-critical methods.
 */
public final class AntiTamperTransformer extends Transformer {

    @Override
    public String name() {
        return "AntiTamper";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();
        boolean classCritical = MethodSelector.hasAnnotation(cn.invisibleAnnotations, SecurityCritical.class.getName()) ||
                                MethodSelector.hasAnnotation(cn.visibleAnnotations, SecurityCritical.class.getName());

        for (MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            boolean methodCritical = classCritical ||
                                     MethodSelector.hasAnnotation(mn.invisibleAnnotations, SecurityCritical.class.getName()) ||
                                     MethodSelector.hasAnnotation(mn.visibleAnnotations, SecurityCritical.class.getName());

            if (methodCritical) {
                injectAntiTamperGuard(mn);
            }
        }
    }

    private void injectAntiTamperGuard(MethodNode mn) {
        InsnList list = new InsnList();
        // Invoke AntiDumpGuard.verify()
        list.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "dev/sweety/transform/vm/security/AntiDumpGuard",
                "verify",
                "()V",
                false
        ));
        mn.instructions.insert(list);
    }
}
