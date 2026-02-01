package dev.sweety.core.config.format;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class YamlFormat extends ConfigFormat {

    private final Map<Class<?>, Yaml> cache = new ConcurrentHashMap<>();

    public final Function<Class<?>, Yaml> provider = (clazz -> {
        if (cache.containsKey(clazz)) return cache.get(clazz);

        DumperOptions yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yamlDumperOptions.setIndent(2);
        yamlDumperOptions.setWidth(80);

        Representer representer = new Representer(yamlDumperOptions);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        LoaderOptions yamlLoaderOptions = new LoaderOptions();
        yamlLoaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
        yamlLoaderOptions.setCodePointLimit(Integer.MAX_VALUE);

        Yaml yml = new Yaml(new Constructor(clazz, yamlLoaderOptions), representer, yamlDumperOptions, yamlLoaderOptions);
        cache.put(clazz, yml);
        return yml;
    });

    public YamlFormat() {
        super(".yml");
    }

    @Override
    public <T> T read(String obj, Class<T> clazz) {
        return provider.apply(clazz).loadAs(obj, clazz);
    }

    @Override
    public <T> String write(T obj) {
        return provider.apply(obj.getClass()).dump(obj);
    }

    @Override
    public <T> void save(T config, Appendable appendable) {
        if (!(appendable instanceof Writer writer)) return;
        provider.apply(config.getClass()).dump(config, writer);
    }

    @Override
    public <T> T load(Reader reader, Class<T> configClass) {
        return provider.apply(configClass).loadAs(reader, configClass);
    }
}
