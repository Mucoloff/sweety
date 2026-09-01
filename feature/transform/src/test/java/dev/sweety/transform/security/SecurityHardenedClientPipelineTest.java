package dev.sweety.transform.security;

import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.constant.IntegerEncodingTransformer;
import dev.sweety.transform.transformers.constant.StringEncryptionTransformer;
import dev.sweety.transform.transformers.control.ConditionalMutationTransformer;
import dev.sweety.transform.transformers.control.GotoNormalizationTransformer;
import dev.sweety.transform.transformers.security.AntiTamperTransformer;
import dev.sweety.transform.transformers.security.InvokeDynamicObfuscator;
import dev.sweety.transform.transformers.security.OpaquePredicateTransformer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityHardenedClientPipelineTest {

    @Transform
    @SecurityCritical
    public static class FullSecurityModule {
        public String decryptAndValidatePayload(String rawToken) {
            int magic = 999;
            if (rawToken.length() > 5) {
                return "SUCCESS_AUTH_" + (magic + rawToken.hashCode());
            }
            return "FAILED_AUTH";
        }
    }

    private static final class TestClassLoader extends ClassLoader {
        TestClassLoader() { super(SecurityHardenedClientPipelineTest.class.getClassLoader()); }
        Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
    }

    @Test
    public void testFullSecurityHardenedPipeline() throws Exception {
        String internalName = FullSecurityModule.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = FullSecurityModule.class.getResourceAsStream("SecurityHardenedClientPipelineTest$FullSecurityModule.class")) {
            assertNotNull(is);
            originalBytes = is.readAllBytes();
        }

        // Full Anti-Crack Stack for Client Security Modules
        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new AntiTamperTransformer())
                .add(new OpaquePredicateTransformer())
                .add(new GotoNormalizationTransformer())
                .add(new ConditionalMutationTransformer())
                .add(new StringEncryptionTransformer())
                .add(new IntegerEncodingTransformer())
                .add(new InvokeDynamicObfuscator())
                .build();

        byte[] transformedBytes = pipeline.transform(originalBytes, internalName + ".class");
        assertNotNull(transformedBytes);

        Class<?> transformedCls = new TestClassLoader().define(FullSecurityModule.class.getName(), transformedBytes);
        Object inst = transformedCls.getDeclaredConstructor().newInstance();
        Method m = transformedCls.getDeclaredMethod("decryptAndValidatePayload", String.class);

        String valid = (String) m.invoke(inst, "secureToken123");
        assertTrue(valid.startsWith("SUCCESS_AUTH_"));

        String invalid = (String) m.invoke(inst, "abc");
        assertEquals("FAILED_AUTH", invalid);
    }
}
