package dev.sweety.config.yml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

public final class YamlUtils {

    private static final ThreadLocal<Yaml> yaml = ThreadLocal.withInitial(() -> {
        DumperOptions yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yamlDumperOptions.setIndent(2);
        yamlDumperOptions.setWidth(80);

        LoaderOptions yamlLoaderOptions = new LoaderOptions();
        yamlLoaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
        yamlLoaderOptions.setCodePointLimit(Integer.MAX_VALUE);

        return new Yaml(yamlLoaderOptions, yamlDumperOptions);
    });

    public static Yaml yaml() {
        return yaml.get();
    }

    public static <T> String write(T obj) {
        return yaml().dump(obj);
    }

    public static <T> String write(T obj, Type type) {
        return yaml().dump(obj);
    }

    public static <T> void save(T config, Appendable appendable) {
        if (appendable instanceof Writer writer) {
            yaml().dump(config, writer);
        } else {
            try {
                appendable.append(write(config));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static <T> void save(T config, Type type, Appendable writer) {
        save(config, writer);
    }

    public static <T> T load(Reader reader, Class<T> configClass) {
        return yaml().loadAs(reader, configClass);
    }

    public static <T> T read(String obj, Class<T> clazz) {
        return yaml().loadAs(obj, clazz);
    }

    public static <T> T load(Reader reader, Type configClass) {
        if (configClass instanceof Class<?>) {
            //noinspection unchecked
            return yaml().loadAs(reader, (Class<T>) configClass);
        }
        return yaml().load(reader);
    }


    public static <T> T read(String obj, Type clazz) {
        if (clazz instanceof Class<?>) {
            //noinspection unchecked
            return yaml().loadAs(obj, (Class<T>) clazz);
        }
        return yaml().load(obj);
    }

    private YamlUtils() {
    }

}
