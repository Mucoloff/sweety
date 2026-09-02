package dev.sweety.transform.transformers.security;

import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injects self-contained inline anti-debug and debugger attachment checks directly into bytecode
 * without referencing any external framework classes.
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
                injectSelfContainedAntiDebug(mn);
            }
        }
    }

    private void injectSelfContainedAntiDebug(MethodNode mn) {
        InsnList list = new InsnList();
        LabelNode trap = new LabelNode();
        LabelNode safe = new LabelNode();

        int tempVar = mn.maxLocals + 2;

        // 1. Get VM input args string and store in temp variable
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/management/ManagementFactory", "getRuntimeMXBean", "()Ljava/lang/management/RuntimeMXBean;", false));
        list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/lang/management/RuntimeMXBean", "getInputArguments", "()Ljava/util/List;", true));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false));
        list.add(new VarInsnNode(Opcodes.ASTORE, tempVar));

        // 2. First check: "-xdebug" (stack depth is exactly 0)
        list.add(new VarInsnNode(Opcodes.ALOAD, tempVar));
        list.add(new LdcInsnNode("-xdebug"));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false));
        list.add(new JumpInsnNode(Opcodes.IFNE, trap));

        // 3. Second check: "jdwp" (stack depth is exactly 0)
        list.add(new VarInsnNode(Opcodes.ALOAD, tempVar));
        list.add(new LdcInsnNode("jdwp"));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false));
        list.add(new JumpInsnNode(Opcodes.IFNE, trap));

        // If safe, continue method execution
        list.add(new JumpInsnNode(Opcodes.GOTO, safe));

        // 4. Trap handler (stack depth is guaranteed 0 on entry)
        list.add(trap);
        list.add(new TypeInsnNode(Opcodes.NEW, "java/lang/SecurityException"));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/SecurityException", "<init>", "()V", false));
        list.add(new InsnNode(Opcodes.ATHROW));

        list.add(safe);

        mn.instructions.insert(list);
    }
}
