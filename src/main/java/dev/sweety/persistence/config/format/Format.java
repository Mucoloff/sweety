package dev.sweety.persistence.config.format;

import dev.sweety.persistence.sql.ConnectionType;

import java.io.Reader;

public enum Format {
    JSON(new JsonFormat());

    public static final Format[] VALUES = values();

    private final ConfigFormat format;

    Format(ConfigFormat format) {
        this.format = format;
    }

    public String extension() {
        return format.getExtension();
    }

    public <T> T read(String obj, Class<T> clazz) {
        return format.read(obj, clazz);
    }

    public <T> void save(T config, Appendable writer) {
        format.save(config, writer);
    }

    public <T> T load(Reader reader, Class<T> configClass) {
        return format.load(reader, configClass);
    }

    public <T> String write(T obj) {
        return format.write(obj);
    }
}