package dev.sweety.patch.applier;

import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.MethodPatch;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PatchClassFileTransformerTest {

    public static class ExampleTarget {
        public void run() {}
    }

    @Test
    public void testTransformerExecution() throws Exception {
        String internalName = ExampleTarget.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = ExampleTarget.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        PatchClassFileTransformer transformer = new PatchClassFileTransformer();
        transformer.register(ClassPatch.of(internalName)
                .patchMethod(MethodPatch.atHead("run", "()V", (mv, op) -> mv.visitInsn(Opcodes.NOP))));

        byte[] transformed = transformer.transform(
                ExampleTarget.class.getClassLoader(),
                internalName,
                null,
                null,
                originalBytes
        );

        assertNotNull(transformed);
        assertTrue(transformed.length > 0);

        // Non-targeted class should return original unmodified bytes
        byte[] untargeted = transformer.transform(
                ExampleTarget.class.getClassLoader(),
                "some/other/Class",
                null,
                null,
                originalBytes
        );
        assertSame(originalBytes, untargeted);
    }
}
