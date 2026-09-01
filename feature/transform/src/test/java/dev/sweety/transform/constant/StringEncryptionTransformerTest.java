package dev.sweety.transform.constant;

import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.constant.StringEncryptionTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class StringEncryptionTransformerTest {

    @Transform
    public static class StringSample {
        public String getSecret() {
            return "sweety-anticheat-secret-token";
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(StringEncryptionTransformerTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testStringEncryptionPreservesBehavior() throws Exception {
        String internalName = StringSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = StringSample.class.getResourceAsStream("StringEncryptionTransformerTest$StringSample.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new StringEncryptionTransformer())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);
        assertFalse(Arrays.equals(originalBytes, transformedBytes), "Bytecode should be modified by StringEncryptionTransformer");

        Class<?> transformedCls = new TestClassLoader().define(StringSample.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method m = transformedCls.getDeclaredMethod("getSecret");
        assertEquals("sweety-anticheat-secret-token", m.invoke(inst));
    }
}
