package dev.sweety.config.common.serialization;

import dev.sweety.serialization.format.StructuredSink;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Bridges the config {@code Map<String,Object>} format to the format-agnostic {@link StructuredSink} SPI.
 *
 * <p>Usage pattern: call {@link #enterField} before writing a scalar or nested structure,
 * call {@link #exitField} after. {@link #toMap} returns the accumulated result.
 *
 * <pre>{@code
 * ConfigSink sink = new ConfigSink();
 * sink.enterField("name"); sink.writeString("Alice"); sink.exitField();
 * sink.enterField("age");  sink.writeInt(30);         sink.exitField();
 * Map<String,Object> data = sink.toMap();
 * }</pre>
 */
public final class ConfigSink implements StructuredSink {

    private final Map<String, Object> root = new TreeMap<>();
    private final ArrayDeque<String> path = new ArrayDeque<>();

    public Map<String, Object> toMap() {
        return root;
    }

    @Override
    public void enterField(String name) {
        path.addLast(name);
    }

    @Override
    public void exitField() {
        path.removeLast();
    }

    @Override
    public void writeBool(boolean v) {
        writeValue(v);
    }

    @Override
    public void writeByte(byte v) {
        writeValue(v);
    }

    @Override
    public void writeShort(short v) {
        writeValue(v);
    }

    @Override
    public void writeChar(char v) {
        writeValue(v);
    }

    @Override
    public void writeInt(int v) {
        writeValue(v);
    }

    @Override
    public void writeLong(long v) {
        writeValue(v);
    }

    @Override
    public void writeFloat(float v) {
        writeValue(v);
    }

    @Override
    public void writeDouble(double v) {
        writeValue(v);
    }

    @Override
    public void writeString(String v) {
        writeValue(v);
    }

    @Override
    public void writeUUID(UUID v) {
        writeValue(v.toString());
    }

    @Override
    public void writeBytes(byte[] v) {
        writeValue(v);
    }

    /**
     * Writes a pre-serialized map at the current path (or merges into root when no field is active).
     */
    public void writeRawMap(Map<String, Object> map) {
        if (path.isEmpty()) root.putAll(map);
        else writeValue(new TreeMap<>(map));
    }

    private void writeValue(Object value) {
        if (path.isEmpty()) throw new IllegalStateException("enterField() required before writing a scalar");
        setNested(root, path.toArray(String[]::new), value);
    }

    @SuppressWarnings("unchecked")
    private static void setNested(Map<String, Object> map, String[] parts, Object value) {
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map<?, ?> m) {
                current = (Map<String, Object>) m;
            } else {
                Map<String, Object> nested = new TreeMap<>();
                current.put(parts[i], nested);
                current = nested;
            }
        }
        current.put(parts[parts.length - 1], value);
    }
}
