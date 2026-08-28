package dev.sweety.transform.vm;

import java.util.Arrays;

/**
 * Dual-array operand stack: {@link #prim} raw bits, {@code ref} object references — sharing ONE index
 * per pushed JVM value (not per real-JVM word), so DUP/POP/SWAP copy/move both arrays blindly without
 * needing to know whether a slot holds a primitive or a reference. This mirrors the same "resolve the
 * ambiguity once, at compile time" idea {@code VMCompiler} already applies for cat-1/cat-2 width (see
 * its class javadoc) — only typed push/pop (used by arithmetic/array/reflection/field ops, which DO
 * know the static type from the compiled bytecode) touch the array that's actually meaningful for that
 * slot. Eliminates the boxing a {@code Deque<Object>} stack forced on every int/long/float/double.
 */
final class VMStack {

    private long[] prim;
    private Object[] ref;
    private int sp;

    VMStack(int initialCapacity) {
        prim = new long[initialCapacity];
        ref = new Object[initialCapacity];
    }

    private void ensure(int needed) {
        if (needed <= prim.length) return;
        int newCap = Math.max(needed, prim.length * 2);
        prim = Arrays.copyOf(prim, newCap);
        ref = Arrays.copyOf(ref, newCap);
    }

    // ── typed push (static type known at the call site) ───────────────────────
    void pushI(int v)      { ensure(sp + 1); prim[sp] = v; ref[sp] = null; sp++; }
    void pushL(long v)     { ensure(sp + 1); prim[sp] = v; ref[sp] = null; sp++; }
    void pushF(float v)    { ensure(sp + 1); prim[sp] = Float.floatToRawIntBits(v); ref[sp] = null; sp++; }
    void pushD(double v)   { ensure(sp + 1); prim[sp] = Double.doubleToRawLongBits(v); ref[sp] = null; sp++; }
    void pushRef(Object v) { ensure(sp + 1); ref[sp] = v; prim[sp] = 0L; sp++; }

    // ── typed pop (static type known at the call site) — also drops the stale
    //    reference slot so a popped object isn't held alive longer than needed ──
    int popI()      { int i = --sp; int v = (int) prim[i]; ref[i] = null; return v; }
    long popL()     { int i = --sp; long v = prim[i]; ref[i] = null; return v; }
    float popF()    { int i = --sp; float v = Float.intBitsToFloat((int) prim[i]); ref[i] = null; return v; }
    double popD()   { int i = --sp; double v = Double.longBitsToDouble(prim[i]); ref[i] = null; return v; }
    Object popRef() { int i = --sp; Object v = ref[i]; ref[i] = null; return v; }

    // ── type-agnostic slot manipulation (DUP/POP/SWAP family) ──────────────────
    /** Drop the top {@code n} slots. */
    void popRaw(int n) {
        for (int i = 0; i < n; i++) { sp--; ref[sp] = null; }
    }

    /** Raw primitive bits of the slot {@code offsetFromTop} below the top (0 = top). Does not pop. */
    long primAt(int offsetFromTop) { return prim[sp - 1 - offsetFromTop]; }

    /** Reference of the slot {@code offsetFromTop} below the top (0 = top). Does not pop. */
    Object refAt(int offsetFromTop) { return ref[sp - 1 - offsetFromTop]; }

    /** Push a raw slot (both arrays) — used to re-push a value read via {@link #primAt}/{@link #refAt}. */
    void pushSlot(long p, Object r) { ensure(sp + 1); prim[sp] = p; ref[sp] = r; sp++; }
}
