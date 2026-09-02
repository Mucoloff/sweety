package dev.sweety.transform.decoy;

import dev.sweety.transform.transformers.decoy.DecoyClassGenerator;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    public void testPolymorphicDecoyBatchArchetypes() throws Exception {
        DecoyClassGenerator generator = new DecoyClassGenerator();
        List<DecoyClassGenerator.DecoyClass> decoys = generator.generateBatch(10, "a", ConfusableDictionary.ILL, 8);

        assertEquals(10, decoys.size());
        TestClassLoader loader = new TestClassLoader();
        Set<String> archetypes = new HashSet<>();

        for (DecoyClassGenerator.DecoyClass decoy : decoys) {
            assertNotNull(decoy.getBytecode());
            assertTrue(decoy.getInternalName().startsWith("a/"));
            archetypes.add(decoy.getArchetype());

            ClassReader reader = new ClassReader(decoy.getBytecode());
            assertEquals(decoy.getInternalName(), reader.getClassName());

            // Test loading and executing decoy class
            Class<?> cls = loader.define(decoy.getInternalName().replace('/', '.'), decoy.getBytecode());
            Object inst = cls.getDeclaredConstructor().newInstance();

            Field[] fields = cls.getDeclaredFields();
            assertTrue(fields.length >= 3, "Decoy must have at least 3 fields");

            // Check field collisions
            int countNamedA = 0;
            for (Field f : fields) {
                if ("a".equals(f.getName())) countNamedA++;
            }
            assertTrue(countNamedA >= 3, "Decoy must have colliding fields named 'a'");
        }

        // Verify all 5 functional archetypes were generated
        assertEquals(5, archetypes.size(), "All 5 functional archetypes must be represented in batch");
    }
}
