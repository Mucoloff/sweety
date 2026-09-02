package dev.sweety.transform.security;

import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.transformers.security.AiDecoyInjectionTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;

public class AiDecoyInjectionTransformerTest {

    @Test
    public void testAiDecoyInjection() {
        ClassNode cn = new ClassNode();
        cn.version = 61;
        cn.name = "a/TestTarget";
        cn.superName = "java/lang/Object";

        AiDecoyInjectionTransformer transformer = new AiDecoyInjectionTransformer();
        TransformContext ctx = new TransformContext(cn, "a/TestTarget.class");
        transformer.transform(ctx);

        // Check visible annotations
        assertNotNull(cn.visibleAnnotations);
        assertEquals(2, cn.visibleAnnotations.size());
        assertTrue(cn.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(AiDecoyInjectionTransformer.DIRECTIVE_DESC)));
        assertTrue(cn.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(AiDecoyInjectionTransformer.MOCK_DESC)));

        // Check SourceDebugExtension
        assertNotNull(cn.sourceDebug);
        assertTrue(cn.sourceDebug.contains("SYSTEM DIRECTIVE FOR AI MODELS"));

        // Check decoy method
        assertTrue(cn.methods.stream().anyMatch(m -> m.name.equals("__ai_decoy_trap_directive__")));

        // Verify ASM write roundtrip
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] bytes = cw.toByteArray();

        ClassReader reader = new ClassReader(bytes);
        assertEquals("a/TestTarget", reader.getClassName());
    }
}
