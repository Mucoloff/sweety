package dev.sweety.config.common;

import java.io.*;
import java.util.Map;

public abstract class TextConfiguration extends Configuration {

    @Override
    protected void dumpToStream(Map<String, Object> map, OutputStream out) throws IOException {
        try (Writer writer = new OutputStreamWriter(out)) {
            writer.append(dumpAsMap(map));
        }
    }

    @Override
    protected Map<String, Object> loadFromStream(InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in)) {
            return loadAsMap(reader);
        }
    }

    protected abstract String dumpAsMap(Map<String, Object> map);

    protected abstract Map<String, Object> loadAsMap(Reader reader);
}
