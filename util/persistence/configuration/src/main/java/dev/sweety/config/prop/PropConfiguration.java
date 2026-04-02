package dev.sweety.config.prop;

import dev.sweety.config.common.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PropConfiguration extends Configuration {

    private static final String TYPE_NULL = "@null";
    private static final String TYPE_STRING = "@str:";
    private static final String TYPE_INT = "@int:";
    private static final String TYPE_LONG = "@long:";
    private static final String TYPE_DOUBLE = "@double:";
    private static final String TYPE_BOOLEAN = "@bool:";
    private static final String TYPE_FLOAT = "@float:";
    private static final String TYPE_BYTE = "@byte:";
    private static final String TYPE_SHORT = "@short:";
    private static final String TYPE_CHAR = "@char:";
    private static final String TYPE_LIST = "@list:[";
    private static final String TYPE_MAP = "@map:{";

    @Override
    protected void dumpToStream(Map<String, Object> map, OutputStream out) throws IOException {
        Properties properties = new Properties();
        flatten(properties, map, "");
        properties.store(out, "Sweety Properties Configuration");
    }

    @Override
    protected Map<String, Object> loadFromStream(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);

        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            putPath(result, key, decode(properties.getProperty(key)));
        }

        return result;
    }

    private void flatten(Properties properties, Map<String, Object> map, String prefix) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                flatten(properties, castMap(nested), key);
                continue;
            }

            properties.setProperty(key, encode(value));
        }
    }

    private void putPath(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map<?, ?> map) {
                //noinspection unchecked
                current = (Map<String, Object>) map;
            } else {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[i], created);
                current = created;
            }
        }

        current.put(parts[parts.length - 1], value);
    }

    private String encode(Object value) {
        switch (value) {
            case null -> {
                return TYPE_NULL;
            }
            case String s -> {
                return s;
            }
            case Integer n -> {
                return String.valueOf(n);
            }
            case Long n -> {
                return String.valueOf(n);
            }
            case Double n -> {
                return String.valueOf(n);
            }
            case Boolean b -> {
                return String.valueOf(b);
            }

            // Preserve less-common scalar types without sacrificing readability for common values.
            case Float n -> {
                return TYPE_FLOAT + n;
            }
            case Byte n -> {
                return TYPE_BYTE + n;
            }
            case Short n -> {
                return TYPE_SHORT + n;
            }
            case Character c -> {
                return TYPE_CHAR + (int) c;
            }
            case List<?> list -> {
                return encodeListReadable(list);
            }
            case Map<?, ?> map -> {
                StringBuilder sb = new StringBuilder(TYPE_MAP);
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (i++ > 0) sb.append(';');
                    sb.append(escape(String.valueOf(entry.getKey())));
                    sb.append('=');
                    sb.append(encode(entry.getValue()));
                }
                sb.append('}');
                return sb.toString();
            }
            default -> {
            }
        }

        return TYPE_STRING + escape(String.valueOf(value));
    }

    private Object decode(String value) {
        if (value == null) return null;

        if (value.equals(TYPE_NULL)) return null;
        if (value.startsWith(TYPE_STRING)) return unescape(value.substring(TYPE_STRING.length()));
        if (value.startsWith(TYPE_INT)) return Integer.parseInt(value.substring(TYPE_INT.length()));
        if (value.startsWith(TYPE_LONG)) return Long.parseLong(value.substring(TYPE_LONG.length()));
        if (value.startsWith(TYPE_DOUBLE)) return Double.parseDouble(value.substring(TYPE_DOUBLE.length()));
        if (value.startsWith(TYPE_BOOLEAN)) return Boolean.parseBoolean(value.substring(TYPE_BOOLEAN.length()));
        if (value.startsWith(TYPE_FLOAT)) return Float.parseFloat(value.substring(TYPE_FLOAT.length()));
        if (value.startsWith(TYPE_BYTE)) return Byte.parseByte(value.substring(TYPE_BYTE.length()));
        if (value.startsWith(TYPE_SHORT)) return Short.parseShort(value.substring(TYPE_SHORT.length()));
        if (value.startsWith(TYPE_CHAR)) return (char) Integer.parseInt(value.substring(TYPE_CHAR.length()));

        if (value.startsWith("[") && value.endsWith("]")) {
            return decodeReadableList(value);
        }

        if (value.startsWith(TYPE_LIST) && value.endsWith("]")) {
            String inner = value.substring(TYPE_LIST.length(), value.length() - 1);
            List<String> chunks = splitTopLevel(inner, ';');
            List<Object> list = new ArrayList<>(chunks.size());
            for (String chunk : chunks) {
                if (!chunk.isEmpty()) {
                    list.add(decode(chunk));
                }
            }
            return list;
        }

        if (value.startsWith(TYPE_MAP) && value.endsWith("}")) {
            String inner = value.substring(TYPE_MAP.length(), value.length() - 1);
            List<String> chunks = splitTopLevel(inner, ';');
            Map<String, Object> map = new LinkedHashMap<>();
            for (String chunk : chunks) {
                int separator = indexOfTopLevel(chunk, '=');
                if (separator <= 0) continue;

                String key = unescape(chunk.substring(0, separator));
                String raw = chunk.substring(separator + 1);
                map.put(key, decode(raw));
            }
            return map;
        }

        return parseLegacyScalar(value);
    }

    private String encodeListReadable(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(encodeReadableListValue(list.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private String encodeReadableListValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quoteIfNeeded(s);
        if (value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> nestedList) return encodeListReadable(nestedList);

        // Fallback for uncommon/complex items to keep round-trip compatibility.
        return encode(value);
    }

    private Object decodeReadableList(String value) {
        String inner = value.substring(1, value.length() - 1).trim();
        if (inner.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> tokens = splitReadableList(inner);
        List<Object> list = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
                list.add(unquote(trimmed));
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                list.add(decodeReadableList(trimmed));
                continue;
            }
            if ("null".equals(trimmed)) {
                list.add(null);
                continue;
            }

            list.add(decode(trimmed));
        }
        return list;
    }

    private List<String> splitReadableList(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int brackets = 0;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                current.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
                continue;
            }

            if (!inQuotes) {
                if (c == '[') brackets++;
                if (c == ']') brackets--;

                if (c == ',' && brackets == 0) {
                    result.add(current.toString());
                    current.setLength(0);
                    continue;
                }
            }

            current.append(c);
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    private String quoteIfNeeded(String input) {
        if (input.isEmpty()) return "\"\"";

        boolean needsQuotes = Character.isWhitespace(input.charAt(0))
                || Character.isWhitespace(input.charAt(input.length() - 1))
                || input.contains(",")
                || input.contains("[")
                || input.contains("]")
                || input.contains("\"")
                || "null".equalsIgnoreCase(input)
                || "true".equalsIgnoreCase(input)
                || "false".equalsIgnoreCase(input)
                || looksNumeric(input);

        if (!needsQuotes) return input;

        return "\"" + input.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String unquote(String input) {
        String inner = input.substring(1, input.length() - 1);
        StringBuilder sb = new StringBuilder(inner.length());
        boolean escaped = false;

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }

        if (escaped) sb.append('\\');
        return sb.toString();
    }

    private boolean looksNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private Object parseLegacyScalar(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
        }

        return value;
    }

    private List<String> splitTopLevel(String input, char separator) {
        List<String> result = new ArrayList<>();
        if (input.isEmpty()) return result;

        StringBuilder current = new StringBuilder();
        int brackets = 0;
        int braces = 0;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                current.append(c);
                escaped = true;
                continue;
            }

            if (c == '[') brackets++;
            if (c == ']') brackets--;
            if (c == '{') braces++;
            if (c == '}') braces--;

            if (c == separator && brackets == 0 && braces == 0) {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    private int indexOfTopLevel(String input, char target) {
        int brackets = 0;
        int braces = 0;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '[') brackets++;
            if (c == ']') brackets--;
            if (c == '{') braces++;
            if (c == '}') braces--;

            if (c == target && brackets == 0 && braces == 0) {
                return i;
            }
        }

        return -1;
    }

    private String escape(String input) {
        return input
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace("=", "\\=")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(":", "\\:")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescape(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    sb.append(c);
                }
                continue;
            }

            switch (c) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> sb.append(c);
            }
            escaped = false;
        }

        if (escaped) {
            sb.append('\\');
        }

        return sb.toString();
    }

    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            casted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return casted;
    }
}
