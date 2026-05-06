package dev.sweety.config.json;

import com.google.gson.Gson;
import dev.sweety.config.common.TextConfiguration;

import java.io.Reader;
import java.util.Map;

public class JsonConfiguration extends TextConfiguration {

    public JsonConfiguration() {
        super("json");
    }

    public JsonConfiguration(String extension) {
        super(extension);
    }

    @Override
    protected String dumpAsMap(Map<String, Object> map) {
        return GsonUtils.gson().toJson(map);
    }

    @Override
    protected Map<String, Object> loadAsMap(Reader reader) {
        //noinspection unchecked
        return GsonUtils.load(reader, Map.class);
    }

}
