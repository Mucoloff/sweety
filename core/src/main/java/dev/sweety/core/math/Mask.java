package dev.sweety.core.math;

import lombok.experimental.UtilityClass;

@UtilityClass
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

    public boolean isPresent(byte _mask, byte index) {
        return (_mask & index) != 0;
    }

    public byte set(byte _mask, byte index) {
        return (byte) (_mask | index);
    }

    public byte clear(byte _mask, byte index) {
        return (byte) (_mask & ~index);
    }

    public byte setState(byte _mask, byte index, boolean state) {
        return state ? set(_mask, index) : clear(_mask, index);
    }

}