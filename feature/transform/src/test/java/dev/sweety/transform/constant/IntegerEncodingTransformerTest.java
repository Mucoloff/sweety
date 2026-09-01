package dev.sweety.transform.constant;

import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.constant.IntegerEncodingTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class IntegerEncodingTransformerTest {

    @Transform
    public static class IntegerSample {
        public int getMagicNumber() {
            return 133742;
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(IntegerEncodingTransformerTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testIntegerEncodingPreservesValue() throws Exception {
        String internalName = IntegerSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = IntegerSample.class.getResourceAsStream("IntegerEncodingTransformerTest$IntegerSample.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new IntegerEncodingTransformer())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);
        assertFalse(Arrays.equals(originalBytes, transformedBytes), "Bytecode should be modified by IntegerEncodingTransformer");

        Class<?> transformedCls = new TestClassLoader().define(IntegerSample.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method m = transformedCls.getDeclaredMethod("getMagicNumber");
        assertEquals(133742, m.invoke(inst));
    }
}
