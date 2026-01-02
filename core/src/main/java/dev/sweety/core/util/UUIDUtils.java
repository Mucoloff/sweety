package dev.sweety.core.util;

import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class UUIDUtils {

    public String formatUuid(String uuidStr) {
        return uuidStr.contains("-")
            ? uuidStr
            : uuidStr.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
    }

    public UUID parseUuid(String uuidStr) {
        return UUID.fromString(formatUuid(uuidStr));
    }
}
