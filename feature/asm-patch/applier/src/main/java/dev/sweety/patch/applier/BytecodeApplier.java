package dev.sweety.patch.applier;

import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.PatchEngine;

/**
 * High-level utility to apply ClassPatch instances directly to byte arrays.
 */
public final class BytecodeApplier {

    private BytecodeApplier() {}

    public static byte[] apply(ClassPatch patch, byte[] classBytes) {
        return apply(patch, classBytes, Thread.currentThread().getContextClassLoader());
    }

    public static byte[] apply(ClassPatch patch, byte[] classBytes, ClassLoader loader) {
        PatchEngine engine = new PatchEngine().classLoader(loader).registerPatch(patch);
        return engine.transform(patch.targetInternalName(), classBytes);
    }
}
