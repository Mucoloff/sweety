package dev.sweety.config.toml;

import dev.sweety.config.common.TextConfiguration;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class TomlConfiguration extends TextConfiguration {

    @Override
    protected String dumpAsMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        writeTableContent(sb, map, "");
        return sb.toString();
    }

    @Override
    protected Map<String, Object> loadAsMap(Reader reader) {
        TomlParseResult result;
        try {
            result = Toml.parse(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        if (result.hasErrors()) {
            throw new IllegalStateException(result.errors().toString());
        }

        return convert(result.toMap());
    }

    // =======================
    // WRITE (Map -> TOML)
    // =======================

    private void writeTableContent(StringBuilder sb, Map<String, Object> map, String parentPath) {
        Map<String, Object> simpleValues = new LinkedHashMap<>();
        Map<String, Map<String, Object>> nestedTables = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> arraysOfTables = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (value) {
                case null -> {
                }
                case Map<?, ?> nested -> nestedTables.put(key, castMap(nested));
                case List<?> list -> {
                    if (isListOfMaps(list)) {
                        List<Map<String, Object>> tableList = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> m) {
                                tableList.add(castMap(m));
                            }
                        }
                        arraysOfTables.put(key, tableList);
                    } else {
                        simpleValues.put(key, value);
                    }
                }
                default -> simpleValues.put(key, value);
            }

        }

        // 1. Simple Values
        for (Map.Entry<String, Object> entry : simpleValues.entrySet()) {
            sb.append(entry.getKey())
                    .append(" = ")
                    .append(formatValue(entry.getValue()))
                    .append("\n");
        }

        // 2. Nested Tables
        for (Map.Entry<String, Map<String, Object>> entry : nestedTables.entrySet()) {
            String fullPath = parentPath.isEmpty() ? entry.getKey() : parentPath + "." + entry.getKey();
            if (hasDirectValues(entry.getValue())) {
                sb.append("\n[").append(fullPath).append("]\n");
            }
            writeTableContent(sb, entry.getValue(), fullPath);
        }

        // 3. Arrays of Tables
        for (Map.Entry<String, List<Map<String, Object>>> entry : arraysOfTables.entrySet()) {
            String fullPath = parentPath.isEmpty() ? entry.getKey() : parentPath + "." + entry.getKey();
            for (Map<String, Object> item : entry.getValue()) {
                sb.append("\n[[").append(fullPath).append("]]\n");
                writeTableContent(sb, item, fullPath);
            }
        }
    }

    private boolean hasDirectValues(Map<String, Object> map) {
        for (Object value : map.values()) {
            if (value == null || value instanceof Map) continue;
            if (value instanceof List) {
                if (isListOfMaps((List<?>) value)) continue;
                return true;
            }
            return true;
        }
        return false;
    }

    private boolean isListOfMaps(List<?> list) {
        return !list.isEmpty() && list.getFirst() instanceof Map;
    }

    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    private String formatValue(Object value) {
        switch (value) {
            case null -> {
                return "null";
            }
            case String s -> {
                return "\"" + escape(s) + "\"";
            }
            case Character c -> {
                return "\"" + escape(String.valueOf(c)) + "\"";
            }
            default -> {
            }
        }

        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }

        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(formatValue(list.get(i)));
                if (i < list.size() - 1) sb.append(", ");
            }
            sb.append("]");
            return sb.toString();
        }

        if (value instanceof Map) {
            throw new IllegalStateException("Nested Map detected in inline value context. " +
                    "This should have been handled as a [table] or [[array of tables]].");
        }

        throw new IllegalArgumentException("Unsupported type: " + value.getClass());
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Map<String, Object> convert(Map<String, Object> input) {
        Map<String, Object> output = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            output.put(entry.getKey(), convertValue(entry.getValue()));
        }

        return output;
    }

    private Object convertValue(Object value) {
        
        if (value instanceof org.tomlj.TomlTable table) return convert(table.toMap());

        if (value instanceof Map<?, ?> map) {
            //noinspection unchecked
            return convert((Map<String, Object>) map);
        }

        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(convertValue(item));
            }
            return result;
        }

        return value;
    }
}
