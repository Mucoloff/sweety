package dev.sweety.transform.vm.state;

public final class VMLocals {

    public long[] prim;
    public Object[] ref;

    public VMLocals(int maxLocals) {
        prim = new long[maxLocals];
        ref = new Object[maxLocals];
    }

    public void reset(int maxLocals) {
        if (prim.length < maxLocals) {
            prim = new long[maxLocals];
            ref = new Object[maxLocals];
        } else {
            java.util.Arrays.fill(prim, 0, maxLocals, 0L);
            java.util.Arrays.fill(ref, 0, maxLocals, null);
        }
    }
}
