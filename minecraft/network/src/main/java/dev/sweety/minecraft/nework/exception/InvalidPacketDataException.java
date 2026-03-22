package dev.sweety.minecraft.nework.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

public class InvalidPacketDataException extends NetworkException {
    public InvalidPacketDataException(String cause, int[] problematicData) {
        super(constructError(cause, problematicData));
    }

    private static String constructError(String c, int[] p) {
        if (p == null || p.length == 0) return c;

        return String.format("%s (Problematic frame: %s)", c, Arrays.stream(p).mapToObj((operand) -> String.format("%02X", operand)).collect(Collectors.joining(" ")));
    }
}
