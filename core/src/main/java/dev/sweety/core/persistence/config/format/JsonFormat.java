package dev.sweety.core.persistence.config.format;

import dev.sweety.core.persistence.config.GsonUtils;

import java.io.Reader;


public class JsonFormat extends ConfigFormat {

    public JsonFormat() {
        super(".json");
    }

    @Override
    public <T> String write(T obj) {
        return GsonUtils.gson().toJson(obj);
    }

    @Override
    public <T> void save(T config, Appendable writer) {
        GsonUtils.gson().toJson(config, writer);
    }

    @Override
    public <T> T load(Reader reader, Class<T> configClass) {
        return GsonUtils.gson().fromJson(reader, configClass);
    }

    @Override
    public <T> T read(String obj, Class<T> clazz) {
        return GsonUtils.gson().fromJson(obj, clazz);
    }
}