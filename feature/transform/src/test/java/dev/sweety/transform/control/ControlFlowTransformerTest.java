package dev.sweety.transform.control;

import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.control.ConditionalMutationTransformer;
import dev.sweety.transform.transformers.control.GotoNormalizationTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ControlFlowTransformerTest {

    @Transform
    public static class BranchSample {
        public int check(int a, int b) {
            if (a > b) {
                return a * 2;
            } else {
                return b * 3;
            }
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(ControlFlowTransformerTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testControlFlowTransformations() throws Exception {
        String internalName = BranchSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = BranchSample.class.getResourceAsStream("ControlFlowTransformerTest$BranchSample.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new GotoNormalizationTransformer())
                .add(new ConditionalMutationTransformer())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);

        Class<?> transformedCls = new TestClassLoader().define(BranchSample.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method m = transformedCls.getDeclaredMethod("check", int.class, int.class);

        assertEquals(20, m.invoke(inst, 10, 5));
        assertEquals(15, m.invoke(inst, 2, 5));
    }
}
