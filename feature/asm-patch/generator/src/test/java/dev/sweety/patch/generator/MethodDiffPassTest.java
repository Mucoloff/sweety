package dev.sweety.patch.generator;

import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.InjectionPoint;
import dev.sweety.patch.applier.BytecodeApplier;
import dev.sweety.patch.generator.pass.MethodDiffPass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodDiffPassTest {

    @Test
    public void testHeadInjectionPassDetection() {
        MethodNode origMethod = new MethodNode(Opcodes.ACC_PUBLIC, "calculate", "(I)I", null, null);
        origMethod.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 1));
        origMethod.instructions.add(new InsnNode(Opcodes.IRETURN));

        MethodNode modMethod = new MethodNode(Opcodes.ACC_PUBLIC, "calculate", "(I)I", null, null);
        modMethod.instructions.add(new InsnNode(Opcodes.NOP)); // Injected at HEAD
        modMethod.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ILOAD, 1));
        modMethod.instructions.add(new InsnNode(Opcodes.IRETURN));

        ClassPatch patch = ClassPatch.of("com/example/TestClass");
        MethodDiffPass.apply(patch, origMethod, modMethod);

        Assertions.assertEquals(1, patch.methodPatches().size());
        Assertions.assertEquals("calculate", patch.methodPatches().get(0).methodName());
        Assertions.assertEquals(InjectionPoint.HEAD, patch.methodPatches().get(0).injectionPoint());
    }

    @Test
    public void testTailInjectionPassDetection() {
        MethodNode origMethod = new MethodNode(Opcodes.ACC_PUBLIC, "cleanup", "()V", null, null);
        origMethod.instructions.add(new InsnNode(Opcodes.RETURN));

        MethodNode modMethod = new MethodNode(Opcodes.ACC_PUBLIC, "cleanup", "()V", null, null);
        modMethod.instructions.add(new InsnNode(Opcodes.NOP)); // Injected before RETURN
        modMethod.instructions.add(new InsnNode(Opcodes.RETURN));

        ClassPatch patch = ClassPatch.of("com/example/TestClass");
        MethodDiffPass.apply(patch, origMethod, modMethod);

        Assertions.assertEquals(1, patch.methodPatches().size());
        Assertions.assertEquals("cleanup", patch.methodPatches().get(0).methodName());
    }
}
