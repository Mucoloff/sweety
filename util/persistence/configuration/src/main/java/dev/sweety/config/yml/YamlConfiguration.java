package dev.sweety.config.yml;

import dev.sweety.config.common.TextConfiguration;

import java.io.*;
import java.util.Map;

public class YamlConfiguration extends TextConfiguration {

    @Override
    protected String dumpAsMap(Map<String, Object> map) {
        return YamlUtils.yaml().dumpAsMap(map);
    }

    @Override
    protected Map<String, Object> loadAsMap(Reader reader) {
        return YamlUtils.yaml().loadAs(reader, Map.class);
    }

}
