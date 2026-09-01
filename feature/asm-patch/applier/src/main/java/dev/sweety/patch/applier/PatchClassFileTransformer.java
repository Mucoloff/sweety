package dev.sweety.patch.applier;

import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.PatchEngine;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Java Agent ClassFileTransformer that applies ASM patches in-memory at runtime during class loading.
 */
public class PatchClassFileTransformer implements ClassFileTransformer {

    private final PatchEngine patchEngine;

    public PatchClassFileTransformer() {
        this(new PatchEngine());
    }

    public PatchClassFileTransformer(PatchEngine patchEngine) {
        this.patchEngine = patchEngine;
    }

    public PatchClassFileTransformer register(ClassPatch patch) {
        this.patchEngine.registerPatch(patch);
        return this;
    }

    public PatchEngine patchEngine() {
        return patchEngine;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null || classfileBuffer == null) {
            return classfileBuffer;
        }

        try {
            return patchEngine.classLoader(loader).transform(className, classfileBuffer);
        } catch (Throwable t) {
            // Return original buffer if transformation fails to prevent breaking JVM boot
            return classfileBuffer;
        }
    }
}
