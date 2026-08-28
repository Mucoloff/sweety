package dev.sweety.transform.vm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import static dev.sweety.transform.vm.VMSupport.classFor;
import static dev.sweety.transform.vm.VMSupport.coerce;
import static dev.sweety.transform.vm.VMSupport.findConstructor;
import static dev.sweety.transform.vm.VMSupport.findField;
import static dev.sweety.transform.vm.VMSupport.findMethod;
import static dev.sweety.transform.vm.VMSupport.paramTypeTags;
import static dev.sweety.transform.vm.VMSupport.popByTag;
import static dev.sweety.transform.vm.VMSupport.pushTyped;
import static dev.sweety.transform.vm.VMSupport.readString;
import static dev.sweety.transform.vm.VMSupport.tagOf;

/**
 * Method invocation and field access, resolved via reflection and cached per bytecode array
 * (the {@code cache} map passed in by {@link VMInterpreter}). Arguments/return values/field values are
 * boxed only right here, at the unavoidable reflection-API boundary ({@code Method.invoke}/
 * {@code Field.get}/{@code Constructor.newInstance} all take/return {@code Object}) — everywhere else
 * in the VM stays unboxed via {@link VMStack}'s typed push/pop.
 */
final class ReflectionOps {

    private ReflectionOps() {}

    static void executeInvoke(VMStack stack, Map<String, Object> cache,
                               ByteBuffer buf, boolean isStatic,
                               boolean isVirtual, boolean isInterface) throws Exception {
        final String owner = readString(buf);
        final String name  = readString(buf);
        final String desc  = readString(buf);

        // <init> is never a Method (constructors aren't in getDeclaredMethods()) and its receiver is
        // always a not-yet-resolved PendingNew placeholder from NEW — handle it before any generic
        // method lookup, using the descriptor alone to know how many args to pop.
        if (name.equals("<init>")) {
            executeConstructorCall(stack, cache, owner, desc);
            return;
        }

        final String cacheKey = (isStatic ? "MS:" : "MI:") + owner + "." + name + desc;

        Method method = (Method) cache.get(cacheKey);
        if (method == null) {
            Class<?> cls = classFor(cache, owner);
            method = findMethod(cls, name, desc);
            method.setAccessible(true);
            cache.put(cacheKey, method);
        }

        final char[] tags = paramTypeTags(desc);
        final Class<?>[] paramTypes = method.getParameterTypes();
        final Object[] callArgs = new Object[paramTypes.length];
        for (int i = paramTypes.length - 1; i >= 0; i--) {
            callArgs[i] = coerce(popByTag(stack, tags[i]), paramTypes[i]);
        }

        Object receiver = isStatic ? null : PendingNew.unwrap(stack.popRef());
        Object result = method.invoke(receiver, callArgs);
        pushTyped(stack, method.getReturnType(), result);
    }

    /**
     * NEW; DUP; INVOKESPECIAL <init>: the receiver is still the {@link PendingNew} placeholder —
     * resolve it in place (mutating the shared holder) instead of pushing a separate value, mirroring
     * the real JVM where the DUP'd reference simply becomes valid once the constructor runs on it.
     */
    private static void executeConstructorCall(VMStack stack, Map<String, Object> cache,
                                                 String owner, String desc) throws Exception {
        final char[] tags = paramTypeTags(desc);
        final int argCount = tags.length;
        final Object[] rawArgs = new Object[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            rawArgs[i] = popByTag(stack, tags[i]);
        }

        Object receiverObj = stack.popRef();
        if (!(receiverObj instanceof PendingNew pending)) {
            throw new IllegalStateException("<init> receiver is not a pending NEW: " + receiverObj);
        }

        Class<?> cls = classFor(cache, pending.internalClassName);
        Constructor<?> ctor = findConstructor(cls, desc);
        ctor.setAccessible(true);

        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] callArgs = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            callArgs[i] = coerce(rawArgs[i], paramTypes[i]);
        }

        pending.resolved = ctor.newInstance(callArgs); // <init> is void — nothing pushed, same as real invokespecial
    }

    static void executeGetField(VMStack stack, Map<String, Object> cache,
                                 ByteBuffer buf, boolean isStatic) throws Exception {
        final String owner = readString(buf);
        final String name  = readString(buf);
        readString(buf); // desc — field.getType() (below) is authoritative; this just advances the buffer

        Field field = resolveField(cache, owner, name, isStatic);
        Object receiver = isStatic ? null : PendingNew.unwrap(stack.popRef());
        pushTyped(stack, field.getType(), field.get(receiver));
    }

    static void executePutField(VMStack stack, Map<String, Object> cache,
                                 ByteBuffer buf, boolean isStatic) throws Exception {
        final String owner = readString(buf);
        final String name  = readString(buf);
        readString(buf); // desc

        Field field = resolveField(cache, owner, name, isStatic);
        Object value = popByTag(stack, tagOf(field.getType()));
        Object receiver = isStatic ? null : PendingNew.unwrap(stack.popRef());
        field.set(receiver, coerce(value, field.getType()));
    }

    private static Field resolveField(Map<String, Object> cache, String owner, String name,
                                       boolean isStatic) throws Exception {
        final String cacheKey = (isStatic ? "FS:" : "FI:") + owner + "." + name;
        Field field = (Field) cache.get(cacheKey);
        if (field == null) {
            field = findField(classFor(cache, owner), name);
            field.setAccessible(true);
            cache.put(cacheKey, field);
        }
        return field;
    }
}
