package dev.sweety.transform.vm;

/**
 * Dual-array local-variable table: raw primitive bits + references, same slot layout the real JVM uses
 * (long/double occupy two consecutive slots — see {@link VMSupport#paramSlotWidths}). Eliminates the
 * per-local boxing a single {@code Object[]} locals array forced on every int/long/float/double.
 */
final class VMLocals {

    final long[] prim;
    final Object[] ref;

    VMLocals(int maxLocals) {
        prim = new long[maxLocals];
        ref = new Object[maxLocals];
    }
}
