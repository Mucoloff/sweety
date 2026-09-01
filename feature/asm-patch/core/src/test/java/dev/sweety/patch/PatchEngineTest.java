package dev.sweety.patch;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PatchEngineTest {

    public static class TargetSample {
        public int calculate(int a, int b) {
            return a + b;
        }
    }

    @Test
    public void testPatchEngineTransformation() throws Exception {
        String internalName = TargetSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = TargetSample.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(is, "Sample class bytecode must be present");
            originalBytes = is.readAllBytes();
        }

        PatchEngine engine = new PatchEngine();
        ClassPatch patch = ClassPatch.of(TargetSample.class.getName())
                .patchMethod(MethodPatch.atHead("calculate", "(II)I", (mv, version) -> {
                    // Inject a NOP at head
                    mv.visitInsn(Opcodes.NOP);
                }));

        engine.registerPatch(patch);
        byte[] transformedBytes = engine.transform(internalName, originalBytes);

        assertNotNull(transformedBytes);
        assertTrue(transformedBytes.length > 0);

        // Verify transformed bytecode with ASM ClassReader
        ClassReader reader = new ClassReader(transformedBytes);
        assertEquals(internalName, reader.getClassName());
    }

    @Test
    public void testSafeClassWriterFallback() {
        SafeClassWriter writer = new SafeClassWriter(0);
        String common = writer.getCommonSuperClass("non/existent/TypeA", "non/existent/TypeB");
        assertEquals("java/lang/Object", common, "SafeClassWriter should fall back to java/lang/Object without throwing");
    }

    @Test
    public void testInvokeAndFieldPatchMatching() {
        ClassPatch patch = ClassPatch.of("com/example/Test")
                .patchMethod(MethodPatch.atInvoke("run", "()V", "java/io/PrintStream", "println", "(Ljava/lang/String;)V", (mv, op) -> {}))
                .patchMethod(MethodPatch.atField("init", "()V", "com/example/Test", "value", "I", (mv, op) -> {}));

        assertEquals(1, patch.findPatches("run", "()V").size());
        assertEquals(1, patch.findPatches("init", "()V").size());
        assertEquals(0, patch.findPatches("other", "()V").size());
    }
}
