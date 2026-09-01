package dev.sweety.transform.remap;

import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.remap.FieldOverloadCollisionTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class FieldOverloadCollisionTransformerTest {

    @Transform
    public static class FieldHolderSample {
        public int health;
        public String username;
        public byte[] sessionKey;
        public long timestamp;

        public void setAll(int h, String u, byte[] k, long t) {
            this.health = h;
            this.username = u;
            this.sessionKey = k;
            this.timestamp = t;
        }

        public String dumpState() {
            return health + ":" + username + ":" + new String(sessionKey) + ":" + timestamp;
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(FieldHolderSample.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testFieldCollisionExecutionPreserved() throws Exception {
        String internalName = FieldHolderSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = FieldHolderSample.class.getResourceAsStream("FieldOverloadCollisionTransformerTest$FieldHolderSample.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new FieldOverloadCollisionTransformer("a"))
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);
        assertFalse(Arrays.equals(originalBytes, transformedBytes));

        Class<?> transformedCls = new TestClassLoader().define(FieldHolderSample.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();

        // Check that fields were indeed renamed with collisions
        Field[] fields = transformedCls.getDeclaredFields();
        assertTrue(fields.length >= 4);

        int countNamedA = 0;
        for (Field f : fields) {
            if ("a".equals(f.getName())) {
                countNamedA++;
            }
        }
        // Different typed fields all named 'a'
        assertTrue(countNamedA >= 4, "All four different-typed fields should share the name 'a'");

        Method setAll = transformedCls.getDeclaredMethod("setAll", int.class, String.class, byte[].class, long.class);
        Method dumpState = transformedCls.getDeclaredMethod("dumpState");

        setAll.invoke(inst, 100, "alice", "SECRET_KEY".getBytes(), 123456789L);
        String state = (String) dumpState.invoke(inst);

        assertEquals("100:alice:SECRET_KEY:123456789", state, "JVM should read and write colliding fields independently without corruption");
    }
}
