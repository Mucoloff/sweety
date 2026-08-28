package dev.sweety.math.mask;

import java.util.Arrays;

public interface Mask {

    byte[] masks();

    default boolean has(int i, int index) {
        final byte idx = (byte) index;
        return (masks()[i] & idx) == idx;
    }

    default void set(int i, int index) {
        masks()[i] |= (byte) index;
    }

    default void unset(int i, int index) {
        masks()[i] &= (byte) ~index;
    }

    default void set(int i, int index, boolean state) {
        if (state) masks()[i] |= (byte) index;
        else masks()[i] &= (byte) ~index;
    }

    default void reset() {
        Arrays.fill(masks(), (byte) 0);
    }

    byte[] INDEXES = {
            0b0001,
            0b0010,
            0b0100,
            0b1000,
            0b10000,
            0b100000,
            0b1000000,
            (byte) 0b10000000
    };

    static byte index(int index) { return (byte) (1 << (index & 7)); }

    static boolean isPresent(byte _mask, byte index) {
        return (_mask & index) == index;
    }

    static boolean isEmpty(byte _mask, byte index) {
        return (_mask & index) != index;
    }

    static byte set(byte _mask, byte index) {
        return (byte) (_mask | index);
    }

    static byte clear(byte _mask, byte index) {
        return (byte) (_mask & ~index);
    }

    static byte setState(byte _mask, byte index, boolean state) {
        return state ? set(_mask, index) : clear(_mask, index);
    }

}
