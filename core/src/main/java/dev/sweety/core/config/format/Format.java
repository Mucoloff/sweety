package dev.sweety.core.config.format;

import java.io.Reader;

public enum Format {
    JSON(new JsonFormat()),
    YAML(new YamlFormat()),;

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