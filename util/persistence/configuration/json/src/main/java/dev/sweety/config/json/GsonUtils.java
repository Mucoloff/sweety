package dev.sweety.config.json;

import com.google.gson.Gson;
import lombok.experimental.UtilityClass;

import java.io.Reader;
import java.lang.reflect.Type;

@UtilityClass
public class GsonUtils {

    private final ThreadLocal<Gson> gson = ThreadLocal.withInitial(() -> new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create());

    public Gson gson() {
        return gson.get();
    }

    public <T> String write(T obj) {
        return gson().toJson(obj);
    }

    public <T> String write(T obj, Type type) {
        return gson().toJson(obj, type);
    }

    public <T> void save(T config, Appendable writer) {
        gson().toJson(config, writer);
    }

    public <T> void save(T config, Type type, Appendable writer) {
        gson().toJson(config, type, writer);
    }

    public <T> T load(Reader reader, Class<T> configClass) {
        return gson().fromJson(reader, configClass);
    }

    public <T> T read(String obj, Class<T> clazz) {
        return gson().fromJson(obj, clazz);
    }

    public <T> T load(Reader reader, Type configClass) {
        return gson().fromJson(reader, configClass);
    }

    public <T> T read(String obj, Type clazz) {
        return gson().fromJson(obj, clazz);
    }

}
