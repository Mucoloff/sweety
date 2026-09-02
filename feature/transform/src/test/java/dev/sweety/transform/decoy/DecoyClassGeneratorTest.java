package dev.sweety.transform.decoy;

import dev.sweety.transform.transformers.decoy.DecoyClassGenerator;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DecoyClassGeneratorTest {

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(DecoyClassGeneratorTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testDecoyGenerationBatch() throws Exception {
        DecoyClassGenerator generator = new DecoyClassGenerator();
        List<DecoyClassGenerator.DecoyClass> decoys = generator.generateBatch(5, "a", ConfusableDictionary.ILL, 8);

        assertEquals(5, decoys.size());
        TestClassLoader loader = new TestClassLoader();

        for (DecoyClassGenerator.DecoyClass decoy : decoys) {
            assertNotNull(decoy.getBytecode());
            assertTrue(decoy.getInternalName().startsWith("a/"));

            ClassReader reader = new ClassReader(decoy.getBytecode());
            assertEquals(decoy.getInternalName(), reader.getClassName());

            // Test loading and executing decoy class
            Class<?> cls = loader.define(decoy.getInternalName().replace('/', '.'), decoy.getBytecode());
            Object inst = cls.getDeclaredConstructor().newInstance();

            Field[] fields = cls.getDeclaredFields();
            assertTrue(fields.length >= 3);

            Method decryptMethod = cls.getDeclaredMethod("decryptLicense", String.class, int.class);
            assertNotNull(decryptMethod);
            Object result = decryptMethod.invoke(inst, "test-license", 1234);
            assertNotNull(result);
        }
    }
}
