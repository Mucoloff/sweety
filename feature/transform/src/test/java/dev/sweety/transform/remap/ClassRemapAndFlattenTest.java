package dev.sweety.transform.remap;

import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.remap.ClassRemapAndFlattenTransformer;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class ClassRemapAndFlattenTest {

    @Transform
    public static class DeepPackageTarget {
        public String execute(String s) {
            return "ok:" + s;
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(ClassRemapAndFlattenTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testClassRemappingAndFlattening() throws Exception {
        String internalName = DeepPackageTarget.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = DeepPackageTarget.class.getResourceAsStream("ClassRemapAndFlattenTest$DeepPackageTarget.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        ClassRemapAndFlattenTransformer transformer = new ClassRemapAndFlattenTransformer("a", ConfusableDictionary.ILL, 10);
        TransformPipeline pipeline = TransformPipeline.builder()
                .add(transformer)
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);

        ClassReader reader = new ClassReader(transformedBytes);
        String remappedName = reader.getClassName();

        assertTrue(remappedName.startsWith("a/"), "Class should be flattened to package 'a/'");
        String simpleName = remappedName.substring(2);
        assertTrue(simpleName.matches("^[Il]+$"), "Class simple name should consist of 'I' and 'l': " + simpleName);

        // Load the remapped class with its new flattened name
        Class<?> remappedCls = new TestClassLoader().define(remappedName.replace('/', '.'), transformedBytes);
        Object inst = remappedCls.getDeclaredConstructor().newInstance();
        Method m = remappedCls.getDeclaredMethod("execute", String.class);

        assertEquals("ok:sweety", m.invoke(inst, "sweety"));
    }
}
