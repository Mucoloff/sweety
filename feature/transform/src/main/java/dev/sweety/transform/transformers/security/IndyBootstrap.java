package dev.sweety.transform.transformers.security;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Dynamic bootstrap resolver for invokedynamic instructions.
 * Conceals direct method linkage from decompilers.
 */
public final class IndyBootstrap {

    private IndyBootstrap() {}

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type,
                                    String targetOwner, String targetName, String targetDesc) throws Exception {
        Class<?> ownerClass = Class.forName(targetOwner.replace('/', '.'));
        MethodHandle handle = lookup.findStatic(ownerClass, targetName, type);
        return new ConstantCallSite(handle);
    }
}
