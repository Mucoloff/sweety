package dev.sweety.transform.vm;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Map;

import static dev.sweety.transform.vm.VMSupport.classFor;
import static dev.sweety.transform.vm.VMSupport.readString;

/** NEW/CHECKCAST/INSTANCEOF/ARRAYLENGTH/THROW/monitor ops. */
final class ObjectOps {

    private ObjectOps() {}

    static void executeNew(VMStack stack, ByteBuffer buf) {
        String internalClassName = readString(buf);
        // Push a placeholder standing in for the real JVM's "uninitialized reference" — DUP shares
        // this same identity, and INVOKESPECIAL <init> (ReflectionOps) resolves it in place once the
        // constructor runs, so every stack slot holding it observes the real object afterward.
        stack.pushRef(new PendingNew(internalClassName));
    }

    static void executeCheckCast(VMStack stack, Map<String, Object> cache, ByteBuffer buf) throws ClassNotFoundException {
        String cls = readString(buf);
        Object v = PendingNew.unwrap(stack.refAt(0)); // peek — CHECKCAST doesn't consume the value
        if (v != null) classFor(cache, cls).cast(v); // throws CCE if wrong
    }

    static void executeInstanceOf(VMStack stack, Map<String, Object> cache, ByteBuffer buf) throws ClassNotFoundException {
        String cls = readString(buf);
        Object v = PendingNew.unwrap(stack.popRef());
        stack.pushI(v != null && classFor(cache, cls).isInstance(v) ? 1 : 0);
    }

    static void executeArrayLength(VMStack stack) {
        stack.pushI(Array.getLength(PendingNew.unwrap(stack.popRef())));
    }

    static void executeThrow(VMStack stack) {
        Object ex = PendingNew.unwrap(stack.popRef());
        if (ex instanceof Throwable t) throw sneakyThrow(t);
        throw new RuntimeException(String.valueOf(ex));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
