package dev.sweety.versioning.util;

import com.google.gson.Gson;
import dev.sweety.data.ObjectUtils;
import dev.sweety.versioning.version.Version;

import java.util.UUID;

public final class Utils {

    private static final ThreadLocal<Gson> GSON = ThreadLocal.withInitial(new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting()::create);

    public static Gson gson() {
        return GSON.get();
    }

    public static byte[] toBytes(UUID uuid) {
        return ObjectUtils.uuidToBytes(uuid);
    }

    public static byte[] toBytes(Version version) {
        int maj = version.major(), min = version.minor(), pat = version.patch();
        return new byte[]{
            (byte)(maj >>> 24), (byte)(maj >>> 16), (byte)(maj >>> 8), (byte) maj,
            (byte)(min >>> 24), (byte)(min >>> 16), (byte)(min >>> 8), (byte) min,
            (byte)(pat >>> 24), (byte)(pat >>> 16), (byte)(pat >>> 8), (byte) pat
        };
    }

    public static byte[] toBytes(int value) {
        return new byte[]{
            (byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte) value
        };
    }

    private Utils() {}
}
