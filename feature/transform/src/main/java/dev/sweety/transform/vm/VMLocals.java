package dev.sweety.transform.vm;

/**
 * Dual-array local-variable table: raw primitive bits + references, same slot layout the real JVM uses
 * (long/double occupy two consecutive slots — see {@link VMSupport#paramSlotWidths}). Eliminates the
 * per-local boxing a single {@code Object[]} locals array forced on every int/long/float/double.
 */
final class VMLocals {

    long[] prim;
    Object[] ref;

    VMLocals(int maxLocals) {
        prim = new long[maxLocals];
        ref = new Object[maxLocals];
    }

    void reset(int maxLocals) {
        if (prim.length < maxLocals) {
            prim = new long[maxLocals];
            ref = new Object[maxLocals];
        } else {
            java.util.Arrays.fill(prim, 0, maxLocals, 0L);
            java.util.Arrays.fill(ref, 0, maxLocals, null);
        }
    }
}
