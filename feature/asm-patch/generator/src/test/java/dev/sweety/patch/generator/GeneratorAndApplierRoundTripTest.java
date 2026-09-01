package dev.sweety.patch.generator;

import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.MethodPatch;
import dev.sweety.patch.applier.BytecodeApplier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class GeneratorAndApplierRoundTripTest {

    public static class SampleCalculator {
        public int add(int a, int b) {
            return a + b;
        }
    }

    @Test
    public void testPatchCodecSerialization() throws Exception {
        ClassPatch patch = ClassPatch.of("com/example/MyService")
                .patchMethod(MethodPatch.atHead("init", "()V", (mv, op) -> mv.visitInsn(Opcodes.NOP)))
                .patchMethod(MethodPatch.atInvoke("execute", "()V", "java/lang/System", "currentTimeMillis", "()J", (mv, op) -> {}));

        byte[] encoded = PatchCodec.encode(patch);
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);

        ClassPatch decoded = PatchCodec.decode(encoded);
        assertEquals("com/example/MyService", decoded.targetInternalName());
        assertEquals(2, decoded.methodPatches().size());
        assertEquals("init", decoded.methodPatches().get(0).methodName());
        assertEquals("execute", decoded.methodPatches().get(1).methodName());
    }

    @Test
    public void testBytecodeApplierRoundTrip() throws Exception {
        String internalName = SampleCalculator.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = SampleCalculator.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        ClassPatch patch = ClassPatch.of(internalName)
                .patchMethod(MethodPatch.atHead("add", "(II)I", (mv, op) -> mv.visitInsn(Opcodes.NOP)));

        byte[] patchedBytes = BytecodeApplier.apply(patch, originalBytes);
        assertNotNull(patchedBytes);
        assertTrue(patchedBytes.length > 0);

        ClassReader reader = new ClassReader(patchedBytes);
        assertEquals(internalName, reader.getClassName());
    }
}
