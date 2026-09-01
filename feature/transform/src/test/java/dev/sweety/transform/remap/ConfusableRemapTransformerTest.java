package dev.sweety.transform.remap;

import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableNameGenerator;
import dev.sweety.transform.transformers.remap.ConfusableRemapTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ConfusableRemapTransformerTest {

    @Test
    public void testConfusableNameGeneratorDictionaries() {
        String nameIll = ConfusableNameGenerator.generate(0, ConfusableDictionary.ILL, 8);
        assertNotNull(nameIll);
        assertEquals(8, nameIll.length());
        assertTrue(nameIll.matches("^[Il]+$"), "Should only contain I and l: " + nameIll);

        String nameOh0 = ConfusableNameGenerator.generate(0, ConfusableDictionary.OH_ZERO, 8);
        assertNotNull(nameOh0);
        assertEquals(8, nameOh0.length());
        assertTrue(nameOh0.matches("^[O0]+$"), "Should only contain O and 0: " + nameOh0);

        String nameRnM = ConfusableNameGenerator.generate(0, ConfusableDictionary.RN_M, 8);
        assertNotNull(nameRnM);
        assertTrue(nameRnM.matches("^(rn|m)+$"), "Should only contain rn and m: " + nameRnM);
    }

    @Transform
    public static class PrivateHelperSample {
        public String execute(String input) {
            return helper(input);
        }

        private String helper(String s) {
            return "processed:" + s;
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(PrivateHelperSample.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testConfusableRemapTransformation() throws Exception {
        String internalName = PrivateHelperSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = PrivateHelperSample.class.getResourceAsStream("ConfusableRemapTransformerTest$PrivateHelperSample.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new ConfusableRemapTransformer(ConfusableDictionary.ILL, 10))
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);
        assertFalse(Arrays.equals(originalBytes, transformedBytes));

        Class<?> transformedCls = new TestClassLoader().define(PrivateHelperSample.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method exec = transformedCls.getDeclaredMethod("execute", String.class);

        assertEquals("processed:test-token", exec.invoke(inst, "test-token"));
    }
}
