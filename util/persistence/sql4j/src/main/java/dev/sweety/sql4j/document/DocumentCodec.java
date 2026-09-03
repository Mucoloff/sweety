package dev.sweety.sql4j.document;

import dev.sweety.config.common.ConfigurationSection;
import dev.sweety.config.common.MapConfigurationSection;
import dev.sweety.config.json.GsonUtils;
import dev.sweety.config.yml.YamlUtils;

import java.util.Map;
import java.util.Objects;

/**
 * Universal document serializer and deserializer bridging Java types to YAML and JSON payloads.
 */
public final class DocumentCodec<T> {

    private final Class<T> targetClass;
    private final DocumentFormat format;

    public DocumentCodec(Class<T> targetClass, DocumentFormat format) {
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass");
        this.format = Objects.requireNonNull(format, "format");
    }

    public static <T> DocumentCodec<T> of(Class<T> targetClass, DocumentFormat format) {
        return new DocumentCodec<>(targetClass, format);
    }

    @SuppressWarnings("unchecked")
    public String serialize(T object) {
        if (object == null) return null;

        if (object instanceof String s) {
            return s;
        }

        if (object instanceof ConfigurationSection section) {
            Map<String, Object> map = section.toMap();
            return format == DocumentFormat.YAML ? YamlUtils.write(map) : GsonUtils.write(map);
        }

        if (format == DocumentFormat.YAML) {
            return YamlUtils.write(object);
        } else {
            return GsonUtils.write(object);
        }
    }

    @SuppressWarnings("unchecked")
    public T deserialize(String content) {
        if (content == null || content.isBlank()) return null;

        if (targetClass == String.class) {
            return (T) content;
        }

        if (ConfigurationSection.class.isAssignableFrom(targetClass)) {
            Map<String, Object> map = (format == DocumentFormat.YAML)
                    ? YamlUtils.read(content, (Class<Map<String, Object>>) (Class<?>) Map.class)
                    : GsonUtils.read(content, (Class<Map<String, Object>>) (Class<?>) Map.class);
            return (T) new MapConfigurationSection(map != null ? map : Map.of());
        }

        if (format == DocumentFormat.YAML) {
            return YamlUtils.read(content, targetClass);
        } else {
            return GsonUtils.read(content, targetClass);
        }
    }

    public DocumentFormat format() {
        return format;
    }

    public Class<T> targetClass() {
        return targetClass;
    }
}
