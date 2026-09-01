package dev.sweety.transform.security;

import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.security.OpaquePredicateTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class OpaquePredicateTransformerTest {

    @Transform
    @SecurityCritical
    public static class SecretPayloadDecryptor {
        public int decrypt(int token, int key) {
            return (token ^ key) + 42;
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(OpaquePredicateTransformerTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testOpaquePredicatePreservesExecution() throws Exception {
        String internalName = SecretPayloadDecryptor.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = SecretPayloadDecryptor.class.getResourceAsStream("OpaquePredicateTransformerTest$SecretPayloadDecryptor.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new OpaquePredicateTransformer())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);
        assertFalse(Arrays.equals(originalBytes, transformedBytes), "Bytecode should have opaque predicates injected");

        Class<?> transformedCls = new TestClassLoader().define(SecretPayloadDecryptor.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method m = transformedCls.getDeclaredMethod("decrypt", int.class, int.class);

        assertEquals((0x1234 ^ 0x4321) + 42, m.invoke(inst, 0x1234, 0x4321));
    }
}
