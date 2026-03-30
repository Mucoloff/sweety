package dev.sweety.versioning.util;

import com.google.gson.Gson;
import dev.sweety.versioning.version.Version;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class Utils {

    private static final ThreadLocal<Gson> GSON = ThreadLocal.withInitial(new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting()::create);

    public static Gson gson() {
        return GSON.get();
    }

    public static String formatUuid(String uuidStr) {
        return uuidStr.contains("-")
                ? uuidStr
                : uuidStr.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
    }

    public static UUID parseUuid(String uuidStr) {
        return UUID.fromString(formatUuid(uuidStr));
    }

    public static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static byte[] toBytes(Version version) {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(version.major());
        buffer.putInt(version.minor());
        buffer.putInt(version.patch());
        return buffer.array();
    }

    public static byte[] toBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private Utils() {}
}
