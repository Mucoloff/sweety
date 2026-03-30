package dev.sweety.config.json;

import com.google.gson.Gson;

import java.io.Reader;
import java.lang.reflect.Type;

public final class GsonUtils {

    private static final ThreadLocal<Gson> gson = ThreadLocal.withInitial(() -> new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create());

    public static Gson gson() {
        return gson.get();
    }

    public static <T> String write(T obj) {
        return gson().toJson(obj);
    }

    public static <T> String write(T obj, Type type) {
        return gson().toJson(obj, type);
    }

    public static <T> void save(T config, Appendable writer) {
        gson().toJson(config, writer);
    }

    public static <T> void save(T config, Type type, Appendable writer) {
        gson().toJson(config, type, writer);
    }

    public static <T> T load(Reader reader, Class<T> configClass) {
        return gson().fromJson(reader, configClass);
    }

    public static <T> T read(String obj, Class<T> clazz) {
        return gson().fromJson(obj, clazz);
    }

    public static <T> T load(Reader reader, Type configClass) {
        return gson().fromJson(reader, configClass);
    }

    public static <T> T read(String obj, Type clazz) {
        return gson().fromJson(obj, clazz);
    }

    private GsonUtils() {

    }

}
