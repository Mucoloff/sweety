package dev.sweety.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.sweety.core.config.adapters.GsonAdapters;
import dev.sweety.core.util.ObjectUtils;
import lombok.experimental.UtilityClass;

import java.io.Reader;

@UtilityClass
public class GsonUtils {

    private final Gson gson = ObjectUtils.make(() -> {
        final GsonBuilder builder = new Gson().newBuilder();
        for (GsonAdapters value : GsonAdapters.VALUES) {
            value.register(builder);
        }
        return builder.disableHtmlEscaping().setPrettyPrinting().create();
    });

    public Gson gson() {
        return gson;
    }

    public <T> String write(T obj) {
        return gson.toJson(obj);
    }

    public <T> void save(T config, Appendable writer) {
        gson.toJson(config, writer);
    }

    public <T> T load(Reader reader, Class<T> configClass) {
        return gson.fromJson(reader, configClass);
    }

    public <T> T read(String obj, Class<T> clazz) {
        return gson.fromJson(obj, clazz);
    }

}
