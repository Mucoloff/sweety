package dev.sweety.transform.security;

import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.security.AntiTamperTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class AntiTamperTransformerTest {

    @Transform
    @SecurityCritical
    public static class LicenseCheckSample {
        public boolean verifyLicense(String key) {
            return "VALID-LICENSE".equals(key);
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(AntiTamperTransformerTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testAntiTamperPassesWhenUncompromised() throws Exception {
        String internalName = LicenseCheckSample.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = LicenseCheckSample.class.getResourceAsStream("AntiTamperTransformerTest$LicenseCheckSample.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new AntiTamperTransformer())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        Class<?> transformedCls = new TestClassLoader().define(LicenseCheckSample.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method m = transformedCls.getDeclaredMethod("verifyLicense", String.class);

        assertEquals(true, m.invoke(inst, "VALID-LICENSE"));
        assertEquals(false, m.invoke(inst, "INVALID"));
    }
}
