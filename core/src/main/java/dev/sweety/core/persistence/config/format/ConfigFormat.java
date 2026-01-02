package dev.sweety.core.persistence.config.format;

import lombok.Getter;

import java.io.Reader;

@Getter
public abstract class ConfigFormat {
    private final String extension;

    public ConfigFormat(String extension) {
        this.extension = extension;
    }

    public abstract <T> T read(String obj, Class<T> clazz);
    public abstract <T> void save(T config, Appendable writer);
    public abstract <T> T load(Reader reader, Class<T> configClass);
    public abstract <T> String write(T obj);
}