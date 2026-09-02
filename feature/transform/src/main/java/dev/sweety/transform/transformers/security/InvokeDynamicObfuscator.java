package dev.sweety.transform.transformers.security;

import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableNameGenerator;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Replaces static method calls with invokedynamic instructions targeting an in-class,
 * self-contained private synthetic Bootstrap Method (BSM), eliminating any external framework linkage.
 */
public final class InvokeDynamicObfuscator extends Transformer {

    public static final String BSM_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/invoke/CallSite;";

    @Override
    public String name() {
        return "InvokeDynamic";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();
        String bsmName = ConfusableNameGenerator.generate(999, ConfusableDictionary.ILL, 10);

        Handle bsmHandle = new Handle(
                Opcodes.H_INVOKESTATIC,
                cn.name,
                bsmName,
                BSM_DESC,
                false
        );

        boolean anyInjected = false;

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(bsmName)) continue;

            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    if (minsn.owner.equals(cn.name)) continue; // Keep local methods as-is

                    // Replace with invokedynamic targeting local BSM
                    InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                            minsn.name,
                            minsn.desc,
                            bsmHandle,
                            minsn.owner,
                            minsn.name,
                            minsn.desc
                    );
                    mn.instructions.set(insn, indy);
                    anyInjected = true;
                }
            }
        }

        if (anyInjected) {
            injectLocalBootstrapMethod(cn, bsmName);
        }
    }

    private void injectLocalBootstrapMethod(ClassNode cn, String bsmName) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals(bsmName)) return;
        }

        MethodNode bsm = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                bsmName,
                BSM_DESC,
                null,
                new String[]{"java/lang/Exception"}
        );

        bsm.visitCode();

        // String ownerReplaced = owner.replace('/', '.');
        bsm.visitVarInsn(Opcodes.ALOAD, 3); // owner
        bsm.visitIntInsn(Opcodes.BIPUSH, '/');
        bsm.visitIntInsn(Opcodes.BIPUSH, '.');
        bsm.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace", "(CC)Ljava/lang/String;", false);
        bsm.visitVarInsn(Opcodes.ASTORE, 6);

        // Class<?> ownerClass = Class.forName(ownerReplaced);
        bsm.visitVarInsn(Opcodes.ALOAD, 6);
        bsm.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        bsm.visitVarInsn(Opcodes.ASTORE, 7);

        // MethodHandle handle = lookup.findStatic(ownerClass, targetName, type);
        bsm.visitVarInsn(Opcodes.ALOAD, 0); // lookup
        bsm.visitVarInsn(Opcodes.ALOAD, 7); // ownerClass
        bsm.visitVarInsn(Opcodes.ALOAD, 4); // targetName
        bsm.visitVarInsn(Opcodes.ALOAD, 2); // type
        bsm.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        bsm.visitVarInsn(Opcodes.ASTORE, 8);

        // return new ConstantCallSite(handle);
        bsm.visitTypeInsn(Opcodes.NEW, "java/lang/invoke/ConstantCallSite");
        bsm.visitInsn(Opcodes.DUP);
        bsm.visitVarInsn(Opcodes.ALOAD, 8);
        bsm.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
        bsm.visitInsn(Opcodes.ARETURN);

        bsm.visitMaxs(4, 9);
        bsm.visitEnd();

        cn.methods.add(bsm);
    }
}
