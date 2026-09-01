package dev.sweety.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

public final class PatchEngine {

    private final Map<String, ClassPatch> patches = new HashMap<>();

    public PatchEngine registerPatch(ClassPatch patch) {
        patches.put(patch.targetInternalName(), patch);
        return this;
    }

    public byte[] transform(String internalName, byte[] classBytes) {
        ClassPatch patch = patches.get(internalName);
        if (patch == null || classBytes == null) return classBytes;

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                for (MethodPatch mp : patch.methodPatches()) {
                    if (mp.methodName().equals(name) && (mp.methodDesc() == null || mp.methodDesc().equals(descriptor))) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                if (mp.injectionPoint() == InjectionPoint.HEAD) {
                                    mp.injector().accept(this, Opcodes.ASM9);
                                }
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (mp.injectionPoint() == InjectionPoint.RETURN) {
                                    if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
                                        mp.injector().accept(this, opcode);
                                    }
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                }
                return mv;
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
