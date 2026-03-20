package dev.sweety.config.yml;

import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;

@UtilityClass
public class YamlUtils {

    private final ThreadLocal<Yaml> yaml = ThreadLocal.withInitial(() -> {
        DumperOptions yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yamlDumperOptions.setIndent(2);
        yamlDumperOptions.setWidth(80);

        LoaderOptions yamlLoaderOptions = new LoaderOptions();
        yamlLoaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
        yamlLoaderOptions.setCodePointLimit(Integer.MAX_VALUE);

        return new Yaml(yamlLoaderOptions, yamlDumperOptions);
    });

    public Yaml yaml() {
        return yaml.get();
    }

    public <T> String write(T obj) {
        return yaml().dump(obj);
    }

    public <T> String write(T obj, Type type) {
        return yaml().dump(obj);
    }

    public <T> void save(T config, Appendable appendable) {
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

    public <T> void save(T config, Type type, Appendable writer) {
        save(config, writer);
    }

    public <T> T load(Reader reader, Class<T> configClass) {
        return yaml().loadAs(reader, configClass);
    }

    public <T> T read(String obj, Class<T> clazz) {
        return yaml().loadAs(obj, clazz);
    }

    public <T> T load(Reader reader, Type configClass) {
        if (configClass instanceof Class<?>) {
            //noinspection unchecked
            return yaml().loadAs(reader, (Class<T>) configClass);
        }
        return yaml().load(reader);
    }


    public <T> T read(String obj, Type clazz) {
        if (clazz instanceof Class<?>) {
            //noinspection unchecked
            return yaml().loadAs(obj, (Class<T>) clazz);
        }
        return yaml().load(obj);
    }

}
