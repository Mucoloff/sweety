package dev.sweety.minecraft.ping.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

public class InvalidPacketDataException extends NetworkException {
    public InvalidPacketDataException(String cause, int[] problematicData) {
        super(constructError(cause, problematicData));
    }

    private static String constructError(String c, int[] p) {
        return p != null && p.length > 0 ? String.format("%s (Problematic frame: %s)", c, Arrays.stream(p).mapToObj((operand) -> String.format("%02X", operand)).collect(Collectors.joining(" "))) : c;
    }
}
