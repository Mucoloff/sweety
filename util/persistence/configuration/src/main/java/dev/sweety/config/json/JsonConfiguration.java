package dev.sweety.config.json;

import com.google.gson.Gson;
import dev.sweety.config.common.TextConfiguration;

import java.io.Reader;
import java.util.Map;

public class JsonConfiguration extends TextConfiguration {

    private final Gson gson = GsonUtils.gson();

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
