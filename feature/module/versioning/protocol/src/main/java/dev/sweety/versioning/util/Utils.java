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
        for (int i = 0; i < value.length; i++) {
            int v = value[i];
            int off = i * 4;
            bytes[off]     = (byte) (v >>> 24);
            bytes[off + 1] = (byte) (v >>> 16);
            bytes[off + 2] = (byte) (v >>> 8);
            bytes[off + 3] = (byte)  v;
        }
        return bytes;
    }

    private Utils() {
    }
}
