package dev.sweety.transform.vm.state;

import java.util.Arrays;

public final class VMStack {

    private long[] prim;
    private Object[] ref;
    private int sp;

    public VMStack(int initialCapacity) {
        prim = new long[initialCapacity];
        ref = new Object[initialCapacity];
    }

    private void ensure(int needed) {
        if (needed <= prim.length) return;
        int newCap = Math.max(needed, prim.length * 2);
        prim = Arrays.copyOf(prim, newCap);
        ref = Arrays.copyOf(ref, newCap);
    }

    public void pushI(int v)      { ensure(sp + 1); prim[sp] = v; ref[sp] = null; sp++; }
    public void pushL(long v)     { ensure(sp + 1); prim[sp] = v; ref[sp] = null; sp++; }
    public void pushF(float v)    { ensure(sp + 1); prim[sp] = Float.floatToRawIntBits(v); ref[sp] = null; sp++; }
    public void pushD(double v)   { ensure(sp + 1); prim[sp] = Double.doubleToRawLongBits(v); ref[sp] = null; sp++; }
    public void pushRef(Object v) { ensure(sp + 1); ref[sp] = v; prim[sp] = 0L; sp++; }

    public int popI()      { int i = --sp; int v = (int) prim[i]; ref[i] = null; return v; }
    public long popL()     { int i = --sp; long v = prim[i]; ref[i] = null; return v; }
    public float popF()    { int i = --sp; float v = Float.intBitsToFloat((int) prim[i]); ref[i] = null; return v; }
    public double popD()   { int i = --sp; double v = Double.longBitsToDouble(prim[i]); ref[i] = null; return v; }
    public Object popRef() { int i = --sp; Object v = ref[i]; ref[i] = null; return v; }

    public void popRaw(int n) {
        for (int i = 0; i < n; i++) { sp--; ref[sp] = null; }
    }

    public long primAt(int offsetFromTop) { return prim[sp - 1 - offsetFromTop]; }
    public Object refAt(int offsetFromTop) { return ref[sp - 1 - offsetFromTop]; }

    public void pushSlot(long p, Object r) { ensure(sp + 1); prim[sp] = p; ref[sp] = r; sp++; }

    public void reset() {
        if (sp > 0) {
            Arrays.fill(ref, 0, sp, null);
            sp = 0;
        }
    }
}
