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
}
