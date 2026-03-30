package dev.sweety.math.mask;

public final class MaskUtil {
    public static final byte[] INDEXES = {
            0b0001,
            0b0010,
            0b0100,
            0b1000,
            0b10000,
            0b100000,
            0b1000000,
            (byte) 0b10000000
    };

    public static byte index(int index) {
        return INDEXES[index % INDEXES.length];
    }

    public static boolean isPresent(byte _mask, byte index) {
        return (_mask & index) == index;
    }

    public static byte set(byte _mask, byte index) {
        return (byte) (_mask | index);
    }

    public static byte clear(byte _mask, byte index) {
        return (byte) (_mask & ~index);
    }

    public static byte setState(byte _mask, byte index, boolean state) {
        return state ? set(_mask, index) : clear(_mask, index);
    }

}