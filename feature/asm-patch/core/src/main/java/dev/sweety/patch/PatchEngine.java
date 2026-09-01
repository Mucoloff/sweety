package dev.sweety.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PatchEngine {

    private final Map<String, ClassPatch> patches = new HashMap<>();
    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    public PatchEngine classLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    public PatchEngine registerPatch(ClassPatch patch) {
        patches.put(patch.targetInternalName(), patch);
        return this;
    }

    public byte[] transform(String internalName, byte[] classBytes) {
        if (internalName == null || classBytes == null) return classBytes;
        String normalized = internalName.replace('.', '/');
        ClassPatch patch = patches.get(normalized);
        if (patch == null) return classBytes;

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, classLoader);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                List<MethodPatch> matchingPatches = patch.findPatches(name, descriptor);
                if (matchingPatches.isEmpty()) return mv;

                MethodVisitor currentMv = mv;
                for (MethodPatch mp : matchingPatches) {
                    final MethodVisitor nextMv = currentMv;
                    currentMv = new MethodVisitor(Opcodes.ASM9, nextMv) {
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

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String mDescriptor, boolean isInterface) {
                            if (mp.injectionPoint() == InjectionPoint.INVOKE) {
                                boolean matchesOwner = mp.targetOwner() == null || mp.targetOwner().equals(owner);
                                boolean matchesName = mp.targetName() == null || mp.targetName().equals(mName);
                                boolean matchesDesc = mp.targetDesc() == null || mp.targetDesc().equals(mDescriptor);

                                if (matchesOwner && matchesName && matchesDesc) {
                                    mp.injector().accept(this, opcode);
                                    return;
                                }
                            }
                            super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fName, String fDescriptor) {
                            if (mp.injectionPoint() == InjectionPoint.FIELD) {
                                boolean matchesOwner = mp.targetOwner() == null || mp.targetOwner().equals(owner);
                                boolean matchesName = mp.targetName() == null || mp.targetName().equals(fName);
                                boolean matchesDesc = mp.targetDesc() == null || mp.targetDesc().equals(fDescriptor);

                                if (matchesOwner && matchesName && matchesDesc) {
                                    mp.injector().accept(this, opcode);
                                    return;
                                }
                            }
                            super.visitFieldInsn(opcode, owner, fName, fDescriptor);
                        }
                    };
                }
                return currentMv;
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
