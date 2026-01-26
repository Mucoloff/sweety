package dev.sweety.core.config.format;

import dev.sweety.core.config.GsonUtils;

import java.io.Reader;

public class JsonFormat extends ConfigFormat {

    public JsonFormat() {
        super(".json");
    }

    @Override
    public <T> String write(T obj) {
        return GsonUtils.write(obj);
    }

    @Override
    public <T> void save(T config, Appendable writer) {
        GsonUtils.save(config,writer);
    }

    @Override
    public <T> T load(Reader reader, Class<T> configClass) {
        return GsonUtils.load(reader, configClass);
    }

    @Override
    public <T> T read(String obj, Class<T> clazz) {
        return GsonUtils.read(obj, clazz);
    }
}