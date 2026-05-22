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
        return toBytes(version.major(), version.minor(), version.patch());
    }

    public static byte[] toBytes(int... value) {
        byte[] bytes = new byte[value.length * 4];
        for (int i = 0; i < value.length * 4; i += 4) {
            bytes[i] = (byte) (value[i] >>> 24);
            bytes[i + 1] = (byte) (value[i] >>> 16);
            bytes[i + 2] = (byte) (value[i] >>> 8);
            bytes[i + 3] = (byte) value[i];
        }
        return bytes;
    }

    private Utils() {
    }
}
