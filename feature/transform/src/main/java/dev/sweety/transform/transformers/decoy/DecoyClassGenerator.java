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
 * Stochastic Control-Flow & Bytecode Synthesizer.
 * Generates highly realistic, unique, and structurally diverse classes with varied
 * field combinations, complex control-flow graphs (loops, switches, nested branches),
 * and rich string/numeric constants ready to be transformed through the full TransformPipeline.
 */
public final class DecoyClassGenerator {

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
            String className = ConfusableNameGenerator.generate(200 + i * 37 + 13, dictionary, nameLength);
            String internalName = packagePrefix.isEmpty() ? className : packagePrefix + "/" + className;
            long seed = 0xCAFEBABE00000000L ^ ((long) i * 0x9E3779B97F4A7C15L);

            byte[] bytes = generateStochasticClass(internalName, new Random(seed), i);
            list.add(new DecoyClass(internalName, bytes));
        }
        return list;
    }

    public byte[] generateStochasticClass(String internalName, Random rng, int classIndex) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        // 1. Stochastic Fields with random types (3 to 6 fields)
        String[] types = new String[]{"I", "J", "Ljava/lang/String;", "[B", "Z", "D", "F", "[I"};
        int fieldCount = 4 + rng.nextInt(3); // 4 to 6 fields
        List<String> chosenTypes = new ArrayList<>();
        for (int f = 0; f < fieldCount; f++) {
            String desc = types[f % types.length];
            chosenTypes.add(desc);
            cw.visitField(Opcodes.ACC_PUBLIC, "a", desc, null, null).visitEnd();
        }

        // 2. <init> method initializing fields with varied pseudo-random constants
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        for (String desc : chosenTypes) {
            init.visitVarInsn(Opcodes.ALOAD, 0);
            switch (desc) {
                case "I" -> {
                    init.visitIntInsn(Opcodes.SIPUSH, 1000 + rng.nextInt(9000));
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
                case "J" -> {
                    init.visitLdcInsn(1700000000000L + rng.nextInt(100000000));
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
                case "Ljava/lang/String;" -> {
                    init.visitLdcInsn("TOKEN_CONTEXT_PAYLOAD_" + classIndex + "_" + rng.nextInt(99999));
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
                case "[B" -> {
                    int arrLen = 16 + rng.nextInt(16);
                    init.visitIntInsn(Opcodes.BIPUSH, arrLen);
                    init.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
                case "Z" -> {
                    init.visitInsn(rng.nextBoolean() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
                case "D" -> {
                    init.visitLdcInsn(rng.nextDouble() * 100.0);
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
                case "F" -> {
                    init.visitLdcInsn(rng.nextFloat() * 50.0f);
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
                case "[I" -> {
                    init.visitIntInsn(Opcodes.BIPUSH, 8);
                    init.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
                    init.visitFieldInsn(Opcodes.PUTFIELD, internalName, "a", desc);
                }
            }
        }
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(4, 1);
        init.visitEnd();

        // 3. Generate 4 to 6 Rich Procedural Methods with diverse Control-Flow Graphs
        int methodCount = 4 + rng.nextInt(3);
        for (int m = 0; m < methodCount; m++) {
            generateProceduralMethod(cw, internalName, rng, m, classIndex);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateProceduralMethod(ClassWriter cw, String internalName, Random rng, int methodIndex, int classIndex) {
        String methodName = "routine_" + methodIndex + "_" + Math.abs(rng.nextInt(1000));
        int methodKind = (methodIndex + classIndex) % 5;

        switch (methodKind) {
            case 0 -> generateLoopAccumulator(cw, internalName, methodName, rng);
            case 1 -> generateBufferTransform(cw, internalName, methodName, rng);
            case 2 -> generateSwitchStateMachine(cw, internalName, methodName, rng);
            case 3 -> generateNestedBranchCrypto(cw, internalName, methodName, rng);
            default -> generateMathVectorPipeline(cw, internalName, methodName, rng);
        }
    }

    // Kind 0: Complex Loop with bitwise operations and accumulator
    private void generateLoopAccumulator(ClassWriter cw, String internalName, String methodName, Random rng) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "(Ljava/lang/String;I)I", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitVarInsn(Opcodes.ISTORE, 3); // acc
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ISTORE, 4); // i

        Label loop = new Label();
        Label end = new Label();
        mv.visitLabel(loop);
        mv.visitVarInsn(Opcodes.ILOAD, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
        mv.visitVarInsn(Opcodes.ISTORE, 5);

        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitIntInsn(Opcodes.BIPUSH, 31);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitVarInsn(Opcodes.ISTORE, 3);

        mv.visitIincInsn(4, 1);
        mv.visitJumpInsn(Opcodes.GOTO, loop);

        mv.visitLabel(end);
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(4, 6);
        mv.visitEnd();
    }

    // Kind 1: Buffer parsing and transformation
    private void generateBufferTransform(ClassWriter cw, String internalName, String methodName, Random rng) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "([BII)[B", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
        mv.visitVarInsn(Opcodes.ASTORE, 4);

        Label loop = new Label();
        Label end = new Label();
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ISTORE, 5);

        mv.visitLabel(loop);
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, end);

        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitInsn(Opcodes.IADD);
        mv.visitInsn(Opcodes.BALOAD);
        mv.visitIntInsn(Opcodes.BIPUSH, rng.nextInt(127) + 1);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitInsn(Opcodes.I2B);
        mv.visitInsn(Opcodes.BASTORE);

        mv.visitIincInsn(5, 1);
        mv.visitJumpInsn(Opcodes.GOTO, loop);

        mv.visitLabel(end);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(6, 6);
        mv.visitEnd();
    }

    // Kind 2: Multi-case State Switch Table
    private void generateSwitchStateMachine(ClassWriter cw, String internalName, String methodName, Random rng) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "(II)I", null, null);
        mv.visitCode();
        Label s0 = new Label();
        Label s1 = new Label();
        Label s2 = new Label();
        Label s3 = new Label();
        Label def = new Label();

        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitTableSwitchInsn(0, 3, def, s0, s1, s2, s3);

        mv.visitLabel(s0);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitIntInsn(Opcodes.BIPUSH, 7);
        mv.visitInsn(Opcodes.ISHL);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitLabel(s1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitIntInsn(Opcodes.BIPUSH, 3);
        mv.visitInsn(Opcodes.ISHR);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitLabel(s2);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitIntInsn(Opcodes.SIPUSH, 0x5A5A);
        mv.visitInsn(Opcodes.IXOR);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitLabel(s3);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitIntInsn(Opcodes.SIPUSH, 1337);
        mv.visitInsn(Opcodes.IADD);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitLabel(def);
        mv.visitInsn(Opcodes.ICONST_M1);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitMaxs(2, 3);
        mv.visitEnd();
    }

    // Kind 3: Nested Branch Condition with String Inspection
    private void generateNestedBranchCrypto(ClassWriter cw, String internalName, String methodName, Random rng) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "(Ljava/lang/String;J)Z", null, null);
        mv.visitCode();
        Label fail = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitJumpInsn(Opcodes.IFNULL, fail);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        mv.visitIntInsn(Opcodes.BIPUSH, 8);
        mv.visitJumpInsn(Opcodes.IF_ICMPLT, fail);

        mv.visitVarInsn(Opcodes.LLOAD, 2);
        mv.visitLdcInsn(1700000000000L);
        mv.visitInsn(Opcodes.LCMP);
        mv.visitJumpInsn(Opcodes.IFLE, fail);

        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitLabel(fail);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitMaxs(4, 4);
        mv.visitEnd();
    }

    // Kind 4: Math & Trigonometry transformations
    private void generateMathVectorPipeline(ClassWriter cw, String internalName, String methodName, Random rng) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "(DDD)[D", null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.BIPUSH, 3);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE);
        mv.visitVarInsn(Opcodes.ASTORE, 7);

        mv.visitVarInsn(Opcodes.ALOAD, 7);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.DLOAD, 1);
        mv.visitVarInsn(Opcodes.DLOAD, 3);
        mv.visitInsn(Opcodes.DMUL);
        mv.visitInsn(Opcodes.DASTORE);

        mv.visitVarInsn(Opcodes.ALOAD, 7);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitVarInsn(Opcodes.DLOAD, 3);
        mv.visitVarInsn(Opcodes.DLOAD, 5);
        mv.visitInsn(Opcodes.DADD);
        mv.visitInsn(Opcodes.DASTORE);

        mv.visitVarInsn(Opcodes.ALOAD, 7);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitVarInsn(Opcodes.DLOAD, 5);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false);
        mv.visitInsn(Opcodes.DASTORE);

        mv.visitVarInsn(Opcodes.ALOAD, 7);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(6, 8);
        mv.visitEnd();
    }
}
