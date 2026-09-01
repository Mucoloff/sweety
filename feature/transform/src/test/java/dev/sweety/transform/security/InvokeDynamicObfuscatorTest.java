package dev.sweety.transform.security;

import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.security.InvokeDynamicObfuscator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class InvokeDynamicObfuscatorTest {

    public static class TargetInvoker {
        public static String invokeHelper(String input) {
            return String.valueOf(Integer.parseInt(input) * 2);
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(InvokeDynamicObfuscatorTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testInvokeDynamicTransformation() throws Exception {
        String internalName = TargetInvoker.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = TargetInvoker.class.getResourceAsStream("InvokeDynamicObfuscatorTest$TargetInvoker.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new InvokeDynamicObfuscator())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);
        assertFalse(Arrays.equals(originalBytes, transformedBytes), "Bytecode should have invokestatic replaced with invokedynamic");

        Class<?> transformedCls = new TestClassLoader().define(TargetInvoker.class.getName(), transformedBytes);
        Method m = transformedCls.getDeclaredMethod("invokeHelper", String.class);
        assertEquals("84", m.invoke(null, "42"));
    }
}
