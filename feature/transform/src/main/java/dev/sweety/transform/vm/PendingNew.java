package dev.sweety.transform.vm;

/**
 * Placeholder pushed by {@code NEW}, mirroring the real JVM's "uninitialized reference" — every
 * {@code DUP} of it shares this same identity, and {@code INVOKESPECIAL <init>} resolves it in
 * place by setting {@link #resolved}, so every other stack slot holding the same instance observes
 * the real object once construction runs. Mutable by design: it stands in for one JVM value whose
 * identity is fixed at {@code NEW} time but whose state (constructed or not) changes once.
 */
final class PendingNew {

    final String internalClassName;
    Object resolved;

    PendingNew(String internalClassName) {
        this.internalClassName = internalClassName;
    }

    /** Returns the constructed instance if this has been resolved, otherwise {@code v} unchanged. */
    static Object unwrap(Object v) {
        return v instanceof PendingNew pending && pending.resolved != null ? pending.resolved : v;
    }
}
