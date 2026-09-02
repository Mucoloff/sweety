package dev.sweety.transform.transformers.decoy;

import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableNameGenerator;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates believable but completely fake/decoy classes (Honey-pots) to mislead reverse engineers,
 * poison call-graph analysis, and hide real security routines inside dozens of synthetic look-alikes.
 */
public final class DecoyClassGenerator {

    private final Random random = new Random(0xDEADBEEF);

    public static class DecoyClass {
        private final String internalName;
        private final byte[] bytecode;

        public DecoyClass(String internalName, byte[] bytecode) {
            this.internalName = internalName;
            this.bytecode = bytecode;
        }

        public String getInternalName() { return internalName; }
        public byte[] getBytecode() { return bytecode; }
    }

    public List<DecoyClass> generateBatch(int count, String packagePrefix, ConfusableDictionary dictionary, int nameLength) {
        List<DecoyClass> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String className = ConfusableNameGenerator.generate(100 + i, dictionary, nameLength);
            String internalName = packagePrefix.isEmpty() ? className : packagePrefix + "/" + className;
            byte[] bytes = generateSingleDecoy(internalName, i);
            list.add(new DecoyClass(internalName, bytes));
        }
        return list;
    }

    public byte[] generateSingleDecoy(String internalName, int seed) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        // Colliding fields
        FieldVisitor fv1 = cw.visitField(Opcodes.ACC_PUBLIC, "a", "I", null, null);
        fv1.visitEnd();
        FieldVisitor fv2 = cw.visitField(Opcodes.ACC_PUBLIC, "a", "Ljava/lang/String;", null, null);
        fv2.visitEnd();
        FieldVisitor fv3 = cw.visitField(Opcodes.ACC_PUBLIC, "a", "[B", null, null);
        fv3.visitEnd();

        // <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitIntInsn(Opcodes.SIPUSH, 1337 + seed);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "I");
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn("DECOY_TOKEN_PAYLOAD_" + seed);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "Ljava/lang/String;");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 1);
        init.visitEnd();

        // Fake License verification routine
        MethodVisitor fakeVerify = cw.visitMethod(Opcodes.ACC_PUBLIC, "verifyPayload", "(Ljava/lang/String;I)Z", null, null);
        fakeVerify.visitCode();
        fakeVerify.visitVarInsn(Opcodes.ALOAD, 1);
        fakeVerify.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);
        fakeVerify.visitVarInsn(Opcodes.ILOAD, 2);
        fakeVerify.visitInsn(Opcodes.IXOR);
        fakeVerify.visitIntInsn(Opcodes.BIPUSH, 42);
        fakeVerify.visitInsn(Opcodes.IAND);
        fakeVerify.visitInsn(Opcodes.IRETURN);
        fakeVerify.visitMaxs(2, 3);
        fakeVerify.visitEnd();

        // Fake Crypto decrypt routine
        MethodVisitor fakeDecrypt = cw.visitMethod(Opcodes.ACC_PUBLIC, "decryptStream", "([B)[B", null, null);
        fakeDecrypt.visitCode();
        fakeDecrypt.visitVarInsn(Opcodes.ALOAD, 1);
        fakeDecrypt.visitInsn(Opcodes.ARETURN);
        fakeDecrypt.visitMaxs(1, 2);
        fakeDecrypt.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
