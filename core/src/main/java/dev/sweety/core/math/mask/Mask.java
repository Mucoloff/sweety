package dev.sweety.core.math.mask;

import java.util.Arrays;

public interface Mask {

    byte[] masks();

    default boolean has(int i, int index){
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

    default void reset(){
        Arrays.fill(masks(), (byte) 0x0);
    }

}
