package dev.sweety.core.math;

public class Mask {
    private static final byte[] INDEXES = {
            0x1,
            0x2,
            0x4,
            0x8,
            0x10,
            0x20,
            0x40,
            0xffffff80
    };

    public static byte index(int index) {
        return INDEXES[index % INDEXES.length];
    }

    private byte _mask;

    public void set(byte index) {
        this._mask |= index;
    }

    public boolean isPresent(byte index) {
        return (this._mask & index) != 0;
    }

    public void reset() {
        this._mask = 0;
    }

}