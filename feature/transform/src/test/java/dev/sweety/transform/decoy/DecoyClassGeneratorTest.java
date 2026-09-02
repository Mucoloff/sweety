package dev.sweety.transform.decoy;

import dev.sweety.transform.transformers.decoy.DecoyClassGenerator;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DecoyClassGeneratorTest {

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(DecoyClassGeneratorTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testStochasticDecoyBatchUniqueness() throws Exception {
        DecoyClassGenerator generator = new DecoyClassGenerator();
        List<DecoyClassGenerator.DecoyClass> decoys = generator.generateBatch(10, "a", ConfusableDictionary.ILL, 8);

        assertEquals(10, decoys.size());
        TestClassLoader loader = new TestClassLoader();
        Set<Integer> byteLengths = new HashSet<>();
        Set<String> classNames = new HashSet<>();

        for (DecoyClassGenerator.DecoyClass decoy : decoys) {
            assertNotNull(decoy.getBytecode());
            assertTrue(decoy.getInternalName().startsWith("a/"));
            classNames.add(decoy.getInternalName());
            byteLengths.add(decoy.getBytecode().length);

            ClassReader reader = new ClassReader(decoy.getBytecode());
            assertEquals(decoy.getInternalName(), reader.getClassName());

            // Test loading and executing decoy class
            Class<?> cls = loader.define(decoy.getInternalName().replace('/', '.'), decoy.getBytecode());
            Object inst = cls.getDeclaredConstructor().newInstance();

            Field[] fields = cls.getDeclaredFields();
            assertTrue(fields.length >= 3, "Decoy must have at least 3 fields");
        }

        // Verify zero duplicate names and rich variety in byte lengths
        assertEquals(10, classNames.size(), "All decoy class names must be unique");
        assertTrue(byteLengths.size() >= 5, "Bytecode sizes must vary across decoys due to stochastic AST synthesis");
    }
}
