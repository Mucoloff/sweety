package dev.sweety.transform.transformers.decoy;

import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableNameGenerator;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic clone decoy classes that match the exact morphology of the obfuscated real class
 * (colliding fields, in-place string decryptor, in-class BSM, opaque predicates).
 */
public final class DecoyClassGenerator {

    private final Random random = new Random(0xCAFEBABE);

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
            String className = ConfusableNameGenerator.generate(200 + i, dictionary, nameLength);
            String internalName = packagePrefix.isEmpty() ? className : packagePrefix + "/" + className;
            byte[] bytes = generateMorphologyCloneDecoy(internalName, i);
            list.add(new DecoyClass(internalName, bytes));
        }
        return list;
    }

    public byte[] generateMorphologyCloneDecoy(String internalName, int seed) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        // 1. Same Colliding fields (int a, String a, byte[] a, long a, boolean a)
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Ljava/lang/String;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "[B", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "J", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Z", null, null).visitEnd();

        // 2. <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitIntInsn(Opcodes.BIPUSH, 80 + seed);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "I");
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn("ENCRYPTED_TOKEN_DECOY_" + seed);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "Ljava/lang/String;");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 1);
        init.visitEnd();

        // 3. Fake License decrypt method with same signature and homoglyph helper names
        String decryptHelperName = ConfusableNameGenerator.generate(50 + seed, ConfusableDictionary.ILL, 12);
        MethodVisitor mDecrypt = cw.visitMethod(Opcodes.ACC_PUBLIC, "decryptLicense", "(Ljava/lang/String;I)Ljava/lang/String;", null, null);
        mDecrypt.visitCode();
        mDecrypt.visitVarInsn(Opcodes.ALOAD, 0);
        mDecrypt.visitVarInsn(Opcodes.ALOAD, 1);
        mDecrypt.visitVarInsn(Opcodes.ILOAD, 2);
        mDecrypt.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internalName, decryptHelperName, "(Ljava/lang/String;I)I", false);
        mDecrypt.visitVarInsn(Opcodes.ISTORE, 3);
        mDecrypt.visitVarInsn(Opcodes.ILOAD, 3);
        mDecrypt.visitIntInsn(Opcodes.BIPUSH, 42);
        Label failLabel = new Label();
        mDecrypt.visitJumpInsn(Opcodes.IF_ICMPNE, failLabel);
        mDecrypt.visitLdcInsn("VALID_LICENSE_RESPONSE");
        mDecrypt.visitInsn(Opcodes.ARETURN);
        mDecrypt.visitLabel(failLabel);
        mDecrypt.visitLdcInsn("INVALID_LICENSE_RESPONSE");
        mDecrypt.visitInsn(Opcodes.ARETURN);
        mDecrypt.visitMaxs(3, 4);
        mDecrypt.visitEnd();

        // 4. Helper internal checksum method
        MethodVisitor mHelper = cw.visitMethod(Opcodes.ACC_PRIVATE, decryptHelperName, "(Ljava/lang/String;I)I", null, null);
        mHelper.visitCode();
        mHelper.visitVarInsn(Opcodes.ILOAD, 2);
        mHelper.visitIntInsn(Opcodes.BIPUSH, 31);
        mHelper.visitInsn(Opcodes.IMUL);
        mHelper.visitInsn(Opcodes.IRETURN);
        mHelper.visitMaxs(2, 3);
        mHelper.visitEnd();

        // 5. In-Place Decryptor method (IlllIIIIlIlI)
        MethodVisitor mn = cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "IlllIIIIlIlI", "(Ljava/lang/String;I)Ljava/lang/String;", null, null);
        mn.visitCode();
        mn.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder", "()Ljava/util/Base64$Decoder;", false);
        mn.visitVarInsn(Opcodes.ALOAD, 0);
        mn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode", "(Ljava/lang/String;)[B", false);
        mn.visitVarInsn(Opcodes.ASTORE, 2);
        mn.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        mn.visitInsn(Opcodes.DUP);
        mn.visitVarInsn(Opcodes.ALOAD, 2);
        mn.visitFieldInsn(Opcodes.GETSTATIC, "java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;");
        mn.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([BLjava/nio/charset/Charset;)V", false);
        mn.visitInsn(Opcodes.ARETURN);
        mn.visitMaxs(4, 3);
        mn.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
