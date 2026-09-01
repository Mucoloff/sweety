package dev.sweety.transform.pipeline;

import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class TransformPipelineFullTest {

    @Transform
    public static class FullFeatureSample {
        public String process(int x) {
            if (x > 100) {
                return "high:" + (x + 50);
            }
            return "low:" + (x * 2);
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(TransformPipelineFullTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testDefaultPipelinePasses() throws Exception {
        String internalName = FullFeatureSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = FullFeatureSample.class.getResourceAsStream("TransformPipelineFullTest$FullFeatureSample.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new dev.sweety.transform.transformers.control.GotoNormalizationTransformer())
                .add(new dev.sweety.transform.transformers.control.ConditionalMutationTransformer())
                .add(new dev.sweety.transform.transformers.constant.StringEncryptionTransformer())
                .add(new dev.sweety.transform.transformers.constant.IntegerEncodingTransformer())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);

        Class<?> transformedCls = new TestClassLoader().define(FullFeatureSample.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method m = transformedCls.getDeclaredMethod("process", int.class);

        assertEquals("high:170", m.invoke(inst, 120));
        assertEquals("low:80", m.invoke(inst, 40));
    }
}
