package dev.sweety.transform.transformers.decoy;

import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableNameGenerator;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stochastic Bytecode Synthesizer generating highly diverse, realistic Decoy (Honey-pot) classes
 * across 5 distinct functional archetypes:
 * 1. Network & Packet Serialization Handlers
 * 2. 3D Vector & Matrix Transformations
 * 3. Cryptographic Permutation & S-Box Hash Pipelines
 * 4. Security & Fake Token Validators
 * 5. State Machine & Bitmask Event Dispatchers
 */
public final class DecoyClassGenerator {

    public static class DecoyClass {
        private final String internalName;
        private final byte[] bytecode;
        private final String archetype;

        public DecoyClass(String internalName, byte[] bytecode, String archetype) {
            this.internalName = internalName;
            this.bytecode = bytecode;
            this.archetype = archetype;
        }

        public String getInternalName() { return internalName; }
        public byte[] getBytecode() { return bytecode; }
        public String getArchetype() { return archetype; }
    }

    public List<DecoyClass> generateBatch(int count, String packagePrefix, ConfusableDictionary dictionary, int nameLength) {
        List<DecoyClass> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String className = ConfusableNameGenerator.generate(200 + i * 17, dictionary, nameLength);
            String internalName = packagePrefix.isEmpty() ? className : packagePrefix + "/" + className;
            int archetypeIndex = i % 5;

            byte[] bytes;
            String archetype;
            switch (archetypeIndex) {
                case 0 -> {
                    bytes = generatePacketSerializerDecoy(internalName, i);
                    archetype = "PacketSerializer";
                }
                case 1 -> {
                    bytes = generateVectorMatrixMathDecoy(internalName, i);
                    archetype = "VectorMatrixMath";
                }
                case 2 -> {
                    bytes = generateCryptoPipelineDecoy(internalName, i);
                    archetype = "CryptoPipeline";
                }
                case 3 -> {
                    bytes = generateTokenValidatorDecoy(internalName, i);
                    archetype = "TokenValidator";
                }
                default -> {
                    bytes = generateStateMachineDecoy(internalName, i);
                    archetype = "StateMachine";
                }
            }

            list.add(new DecoyClass(internalName, bytes, archetype));
        }
        return list;
    }

    // -------------------------------------------------------------------------------------------------
    // Archetype 1: Network Packet Serializer (VarInt, CRC32, Framing, Checksum)
    // -------------------------------------------------------------------------------------------------
    private byte[] generatePacketSerializerDecoy(String internalName, int seed) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC, "a", "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "[B", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "J", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Ljava/lang/String;", null, null).visitEnd();

        // <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitIntInsn(Opcodes.SIPUSH, 256 + seed);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "I");
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn("PACKET_BUFFER_INITIALIZED_" + seed);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "Ljava/lang/String;");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 1);
        init.visitEnd();

        // Method 1: writeVarInt
        MethodVisitor mVarInt = cw.visitMethod(Opcodes.ACC_PUBLIC, "writeVarInt", "(I[BI)I", null, null);
        mVarInt.visitCode();
        Label loop = new Label();
        Label end = new Label();
        mVarInt.visitLabel(loop);
        mVarInt.visitVarInsn(Opcodes.ILOAD, 1);
        mVarInt.visitIntInsn(Opcodes.BIPUSH, -128);
        mVarInt.visitInsn(Opcodes.IAND);
        mVarInt.visitJumpInsn(Opcodes.IFEQ, end);
        mVarInt.visitVarInsn(Opcodes.ALOAD, 2);
        mVarInt.visitVarInsn(Opcodes.ILOAD, 3);
        mVarInt.visitVarInsn(Opcodes.ILOAD, 1);
        mVarInt.visitIntInsn(Opcodes.BIPUSH, 127);
        mVarInt.visitInsn(Opcodes.IAND);
        mVarInt.visitIntInsn(Opcodes.BIPUSH, 128);
        mVarInt.visitInsn(Opcodes.IOR);
        mVarInt.visitInsn(Opcodes.I2B);
        mVarInt.visitInsn(Opcodes.BASTORE);
        mVarInt.visitIincInsn(3, 1);
        mVarInt.visitVarInsn(Opcodes.ILOAD, 1);
        mVarInt.visitIntInsn(Opcodes.BIPUSH, 7);
        mVarInt.visitInsn(Opcodes.IUSHR);
        mVarInt.visitVarInsn(Opcodes.ISTORE, 1);
        mVarInt.visitJumpInsn(Opcodes.GOTO, loop);
        mVarInt.visitLabel(end);
        mVarInt.visitVarInsn(Opcodes.ALOAD, 2);
        mVarInt.visitVarInsn(Opcodes.ILOAD, 3);
        mVarInt.visitVarInsn(Opcodes.ILOAD, 1);
        mVarInt.visitInsn(Opcodes.I2B);
        mVarInt.visitInsn(Opcodes.BASTORE);
        mVarInt.visitVarInsn(Opcodes.ILOAD, 3);
        mVarInt.visitInsn(Opcodes.ICONST_1);
        mVarInt.visitInsn(Opcodes.IADD);
        mVarInt.visitInsn(Opcodes.IRETURN);
        mVarInt.visitMaxs(5, 4);
        mVarInt.visitEnd();

        // Method 2: computeChecksum
        MethodVisitor mCrc = cw.visitMethod(Opcodes.ACC_PUBLIC, "computeChecksum", "([B)J", null, null);
        mCrc.visitCode();
        mCrc.visitTypeInsn(Opcodes.NEW, "java/util/zip/CRC32");
        mCrc.visitInsn(Opcodes.DUP);
        mCrc.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/zip/CRC32", "<init>", "()V", false);
        mCrc.visitVarInsn(Opcodes.ASTORE, 2);
        mCrc.visitVarInsn(Opcodes.ALOAD, 2);
        mCrc.visitVarInsn(Opcodes.ALOAD, 1);
        mCrc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/zip/CRC32", "update", "([B)V", false);
        mCrc.visitVarInsn(Opcodes.ALOAD, 2);
        mCrc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/zip/CRC32", "getValue", "()J", false);
        mCrc.visitInsn(Opcodes.LRETURN);
        mCrc.visitMaxs(2, 3);
        mCrc.visitEnd();

        // Method 3: framePayload
        MethodVisitor mFrame = cw.visitMethod(Opcodes.ACC_PUBLIC, "framePayload", "([BII)[B", null, null);
        mFrame.visitCode();
        mFrame.visitVarInsn(Opcodes.ILOAD, 3);
        mFrame.visitIntInsn(Opcodes.BIPUSH, 4);
        mFrame.visitInsn(Opcodes.IADD);
        mFrame.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mFrame.visitVarInsn(Opcodes.ASTORE, 4);
        mFrame.visitVarInsn(Opcodes.ALOAD, 1);
        mFrame.visitVarInsn(Opcodes.ILOAD, 2);
        mFrame.visitVarInsn(Opcodes.ALOAD, 4);
        mFrame.visitInsn(Opcodes.ICONST_4);
        mFrame.visitVarInsn(Opcodes.ILOAD, 3);
        mFrame.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
        mFrame.visitVarInsn(Opcodes.ALOAD, 4);
        mFrame.visitInsn(Opcodes.ARETURN);
        mFrame.visitMaxs(5, 5);
        mFrame.visitEnd();

        injectSharedDecoyDecryptor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // -------------------------------------------------------------------------------------------------
    // Archetype 2: 3D Vector & 4x4 Matrix Transformations (Trigonometry, Dot Product, Normalize)
    // -------------------------------------------------------------------------------------------------
    private byte[] generateVectorMatrixMathDecoy(String internalName, int seed) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC, "a", "D", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "F", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "[D", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "I", null, null).visitEnd();

        // <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(3.141592653589793);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "D");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(3, 1);
        init.visitEnd();

        // Method 1: transformVector
        MethodVisitor mVec = cw.visitMethod(Opcodes.ACC_PUBLIC, "transformVector", "(DDDD)[D", null, null);
        mVec.visitCode();
        mVec.visitVarInsn(Opcodes.DLOAD, 7);
        mVec.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "cos", "(D)D", false);
        mVec.visitVarInsn(Opcodes.DSTORE, 9);
        mVec.visitVarInsn(Opcodes.DLOAD, 7);
        mVec.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sin", "(D)D", false);
        mVec.visitVarInsn(Opcodes.DSTORE, 11);

        mVec.visitIntInsn(Opcodes.BIPUSH, 3);
        mVec.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE);
        mVec.visitInsn(Opcodes.DUP);
        mVec.visitInsn(Opcodes.ICONST_0);
        mVec.visitVarInsn(Opcodes.DLOAD, 1);
        mVec.visitVarInsn(Opcodes.DLOAD, 9);
        mVec.visitInsn(Opcodes.DMUL);
        mVec.visitVarInsn(Opcodes.DLOAD, 5);
        mVec.visitVarInsn(Opcodes.DLOAD, 11);
        mVec.visitInsn(Opcodes.DMUL);
        mVec.visitInsn(Opcodes.DSUB);
        mVec.visitInsn(Opcodes.DASTORE);

        mVec.visitInsn(Opcodes.DUP);
        mVec.visitInsn(Opcodes.ICONST_1);
        mVec.visitVarInsn(Opcodes.DLOAD, 3);
        mVec.visitInsn(Opcodes.DASTORE);

        mVec.visitInsn(Opcodes.DUP);
        mVec.visitInsn(Opcodes.ICONST_2);
        mVec.visitVarInsn(Opcodes.DLOAD, 1);
        mVec.visitVarInsn(Opcodes.DLOAD, 11);
        mVec.visitInsn(Opcodes.DMUL);
        mVec.visitVarInsn(Opcodes.DLOAD, 5);
        mVec.visitVarInsn(Opcodes.DLOAD, 9);
        mVec.visitInsn(Opcodes.DMUL);
        mVec.visitInsn(Opcodes.DADD);
        mVec.visitInsn(Opcodes.DASTORE);

        mVec.visitInsn(Opcodes.ARETURN);
        mVec.visitMaxs(9, 13);
        mVec.visitEnd();

        // Method 2: dotProduct
        MethodVisitor mDot = cw.visitMethod(Opcodes.ACC_PUBLIC, "dotProduct", "([D[D)D", null, null);
        mDot.visitCode();
        mDot.visitVarInsn(Opcodes.ALOAD, 1);
        mDot.visitInsn(Opcodes.ICONST_0);
        mDot.visitInsn(Opcodes.DALOAD);
        mDot.visitVarInsn(Opcodes.ALOAD, 2);
        mDot.visitInsn(Opcodes.ICONST_0);
        mDot.visitInsn(Opcodes.DALOAD);
        mDot.visitInsn(Opcodes.DMUL);
        mDot.visitVarInsn(Opcodes.ALOAD, 1);
        mDot.visitInsn(Opcodes.ICONST_1);
        mDot.visitInsn(Opcodes.DALOAD);
        mDot.visitVarInsn(Opcodes.ALOAD, 2);
        mDot.visitInsn(Opcodes.ICONST_1);
        mDot.visitInsn(Opcodes.DALOAD);
        mDot.visitInsn(Opcodes.DMUL);
        mDot.visitInsn(Opcodes.DADD);
        mDot.visitInsn(Opcodes.DRETURN);
        mDot.visitMaxs(5, 3);
        mDot.visitEnd();

        injectSharedDecoyDecryptor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // -------------------------------------------------------------------------------------------------
    // Archetype 3: Cryptographic S-Box Permutations & Hash Pipelines
    // -------------------------------------------------------------------------------------------------
    private byte[] generateCryptoPipelineDecoy(String internalName, int seed) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC, "a", "[B", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Z", null, null).visitEnd();

        // <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitIntInsn(Opcodes.SIPUSH, 256);
        init.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "[B");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 1);
        init.visitEnd();

        // Method 1: substituteBox
        MethodVisitor mSbox = cw.visitMethod(Opcodes.ACC_PUBLIC, "substituteBox", "([BI)[B", null, null);
        mSbox.visitCode();
        mSbox.visitVarInsn(Opcodes.ALOAD, 1);
        mSbox.visitInsn(Opcodes.ARRAYLENGTH);
        mSbox.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mSbox.visitVarInsn(Opcodes.ASTORE, 3);
        mSbox.visitInsn(Opcodes.ICONST_0);
        mSbox.visitVarInsn(Opcodes.ISTORE, 4);

        Label loop = new Label();
        Label end = new Label();
        mSbox.visitLabel(loop);
        mSbox.visitVarInsn(Opcodes.ILOAD, 4);
        mSbox.visitVarInsn(Opcodes.ALOAD, 1);
        mSbox.visitInsn(Opcodes.ARRAYLENGTH);
        mSbox.visitJumpInsn(Opcodes.IF_ICMPGE, end);

        mSbox.visitVarInsn(Opcodes.ALOAD, 3);
        mSbox.visitVarInsn(Opcodes.ILOAD, 4);
        mSbox.visitVarInsn(Opcodes.ALOAD, 1);
        mSbox.visitVarInsn(Opcodes.ILOAD, 4);
        mSbox.visitInsn(Opcodes.BALOAD);
        mSbox.visitVarInsn(Opcodes.ILOAD, 2);
        mSbox.visitInsn(Opcodes.IXOR);
        mSbox.visitIntInsn(Opcodes.BIPUSH, 0x1F);
        mSbox.visitInsn(Opcodes.IAND);
        mSbox.visitInsn(Opcodes.I2B);
        mSbox.visitInsn(Opcodes.BASTORE);

        mSbox.visitIincInsn(4, 1);
        mSbox.visitJumpInsn(Opcodes.GOTO, loop);

        mSbox.visitLabel(end);
        mSbox.visitVarInsn(Opcodes.ALOAD, 3);
        mSbox.visitInsn(Opcodes.ARETURN);
        mSbox.visitMaxs(5, 5);
        mSbox.visitEnd();

        // Method 2: computeFeistelHash
        MethodVisitor mFeistel = cw.visitMethod(Opcodes.ACC_PUBLIC, "computeFeistelHash", "([B)I", null, null);
        mFeistel.visitCode();
        mFeistel.visitIntInsn(Opcodes.SIPUSH, 0x1337);
        mFeistel.visitVarInsn(Opcodes.ISTORE, 2);
        mFeistel.visitInsn(Opcodes.ICONST_0);
        mFeistel.visitVarInsn(Opcodes.ISTORE, 3);
        Label fLoop = new Label();
        Label fEnd = new Label();
        mFeistel.visitLabel(fLoop);
        mFeistel.visitVarInsn(Opcodes.ILOAD, 3);
        mFeistel.visitVarInsn(Opcodes.ALOAD, 1);
        mFeistel.visitInsn(Opcodes.ARRAYLENGTH);
        mFeistel.visitJumpInsn(Opcodes.IF_ICMPGE, fEnd);
        mFeistel.visitVarInsn(Opcodes.ILOAD, 2);
        mFeistel.visitIntInsn(Opcodes.BIPUSH, 31);
        mFeistel.visitInsn(Opcodes.IMUL);
        mFeistel.visitVarInsn(Opcodes.ALOAD, 1);
        mFeistel.visitVarInsn(Opcodes.ILOAD, 3);
        mFeistel.visitInsn(Opcodes.BALOAD);
        mFeistel.visitInsn(Opcodes.IXOR);
        mFeistel.visitVarInsn(Opcodes.ISTORE, 2);
        mFeistel.visitIincInsn(3, 1);
        mFeistel.visitJumpInsn(Opcodes.GOTO, fLoop);
        mFeistel.visitLabel(fEnd);
        mFeistel.visitVarInsn(Opcodes.ILOAD, 2);
        mFeistel.visitInsn(Opcodes.IRETURN);
        mFeistel.visitMaxs(3, 4);
        mFeistel.visitEnd();

        injectSharedDecoyDecryptor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // -------------------------------------------------------------------------------------------------
    // Archetype 4: Security Token Validator (JWT, Nonce Expiration, Challenge Signature)
    // -------------------------------------------------------------------------------------------------
    private byte[] generateTokenValidatorDecoy(String internalName, int seed) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Ljava/lang/String;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "J", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Z", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "I", null, null).visitEnd();

        // <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn("eyJhbGciOiJIUzI1NiJ9.payload." + seed);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "Ljava/lang/String;");
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(1760000000000L + seed * 1000L);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "J");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(3, 1);
        init.visitEnd();

        // Method 1: validateTokenNonce
        MethodVisitor mVal = cw.visitMethod(Opcodes.ACC_PUBLIC, "validateTokenNonce", "(Ljava/lang/String;J)Z", null, null);
        mVal.visitCode();
        mVal.visitVarInsn(Opcodes.ALOAD, 1);
        Label fail = new Label();
        mVal.visitJumpInsn(Opcodes.IFNULL, fail);
        mVal.visitVarInsn(Opcodes.ALOAD, 1);
        mVal.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mVal.visitIntInsn(Opcodes.BIPUSH, 16);
        mVal.visitJumpInsn(Opcodes.IF_ICMPLT, fail);
        mVal.visitVarInsn(Opcodes.LLOAD, 2);
        mVal.visitVarInsn(Opcodes.ALOAD, 0);
        mVal.visitFieldInsn(Opcodes.GETFIELD, internalName, "a", "J");
        mVal.visitInsn(Opcodes.LCMP);
        mVal.visitJumpInsn(Opcodes.IFGE, fail);
        mVal.visitInsn(Opcodes.ICONST_1);
        mVal.visitInsn(Opcodes.IRETURN);
        mVal.visitLabel(fail);
        mVal.visitInsn(Opcodes.ICONST_0);
        mVal.visitInsn(Opcodes.IRETURN);
        mVal.visitMaxs(4, 4);
        mVal.visitEnd();

        // Method 2: parsePayloadHeader
        MethodVisitor mHeader = cw.visitMethod(Opcodes.ACC_PUBLIC, "parsePayloadHeader", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        mHeader.visitCode();
        mHeader.visitVarInsn(Opcodes.ALOAD, 1);
        mHeader.visitLdcInsn(".");
        mHeader.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false);
        mHeader.visitVarInsn(Opcodes.ISTORE, 2);
        mHeader.visitVarInsn(Opcodes.ILOAD, 2);
        Label hFail = new Label();
        mHeader.visitJumpInsn(Opcodes.IFLE, hFail);
        mHeader.visitVarInsn(Opcodes.ALOAD, 1);
        mHeader.visitInsn(Opcodes.ICONST_0);
        mHeader.visitVarInsn(Opcodes.ILOAD, 2);
        mHeader.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false);
        mHeader.visitInsn(Opcodes.ARETURN);
        mHeader.visitLabel(hFail);
        mHeader.visitLdcInsn("");
        mHeader.visitInsn(Opcodes.ARETURN);
        mHeader.visitMaxs(3, 3);
        mHeader.visitEnd();

        injectSharedDecoyDecryptor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // -------------------------------------------------------------------------------------------------
    // Archetype 5: State Machine & Bitmask Event Dispatcher (TABLESWITCH, Lookup, Transitions)
    // -------------------------------------------------------------------------------------------------
    private byte[] generateStateMachineDecoy(String internalName, int seed) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC, "a", "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Z", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "a", "Ljava/lang/String;", null, null).visitEnd();

        // <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", "I");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 1);
        init.visitEnd();

        // Method 1: transitionState
        MethodVisitor mState = cw.visitMethod(Opcodes.ACC_PUBLIC, "transitionState", "(II)I", null, null);
        mState.visitCode();
        Label s0 = new Label();
        Label s1 = new Label();
        Label s2 = new Label();
        Label def = new Label();

        mState.visitVarInsn(Opcodes.ILOAD, 1);
        mState.visitTableSwitchInsn(0, 2, def, s0, s1, s2);

        mState.visitLabel(s0);
        mState.visitVarInsn(Opcodes.ILOAD, 2);
        mState.visitIntInsn(Opcodes.BIPUSH, 1);
        mState.visitInsn(Opcodes.IOR);
        mState.visitInsn(Opcodes.IRETURN);

        mState.visitLabel(s1);
        mState.visitVarInsn(Opcodes.ILOAD, 2);
        mState.visitIntInsn(Opcodes.BIPUSH, 2);
        mState.visitInsn(Opcodes.IXOR);
        mState.visitInsn(Opcodes.IRETURN);

        mState.visitLabel(s2);
        mState.visitVarInsn(Opcodes.ILOAD, 2);
        mState.visitIntInsn(Opcodes.BIPUSH, 4);
        mState.visitInsn(Opcodes.IAND);
        mState.visitInsn(Opcodes.IRETURN);

        mState.visitLabel(def);
        mState.visitInsn(Opcodes.ICONST_M1);
        mState.visitInsn(Opcodes.IRETURN);

        mState.visitMaxs(2, 3);
        mState.visitEnd();

        // Method 2: isTerminalState
        MethodVisitor mTerm = cw.visitMethod(Opcodes.ACC_PUBLIC, "isTerminalState", "(I)Z", null, null);
        mTerm.visitCode();
        mTerm.visitVarInsn(Opcodes.ILOAD, 1);
        mTerm.visitIntInsn(Opcodes.BIPUSH, 7);
        Label tMatch = new Label();
        mTerm.visitJumpInsn(Opcodes.IF_ICMPEQ, tMatch);
        mTerm.visitInsn(Opcodes.ICONST_0);
        mTerm.visitInsn(Opcodes.IRETURN);
        mTerm.visitLabel(tMatch);
        mTerm.visitInsn(Opcodes.ICONST_1);
        mTerm.visitInsn(Opcodes.IRETURN);
        mTerm.visitMaxs(2, 2);
        mTerm.visitEnd();

        injectSharedDecoyDecryptor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // -------------------------------------------------------------------------------------------------
    // Shared Decoy Helper: In-Place String Decryptor (IlllIIIIlIlI)
    // -------------------------------------------------------------------------------------------------
    private void injectSharedDecoyDecryptor(ClassWriter cw) {
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
    }
}
