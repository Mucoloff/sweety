package dev.sweety.core.math.mask;

import java.util.Arrays;

public interface Mask {

    byte[] masks();

    default boolean has(int i, byte index){
        return (masks()[i] & index) == index;
    }

    default void set(int i, byte index) {
        masks()[i] |= index;
    }

    default void unset(int i, byte index) {
        masks()[i] &= (byte) ~index;
    }

    default void set(int i, byte index, boolean state) {
        if (state) masks()[i] |= index;
        else masks()[i] &= (byte) ~index;
    }

    default void reset(){
        Arrays.fill(masks(), (byte) 0x0);
    }

}
