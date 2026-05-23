package dev.sweety.config.common.serialization;

import dev.sweety.serialization.format.StructuredSource;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges a {@code Map<String,Object>} to the format-agnostic {@link StructuredSource} SPI.
 *
 * <p>Usage pattern: call {@link #enterField} before reading a scalar or nested structure,
 * call {@link #exitField} after.
 *
 * <pre>{@code
 * ConfigSource src = new ConfigSource(data);
 * src.enterField("name"); String name = src.readString(); src.exitField();
 * src.enterField("age");  int age = src.readInt();        src.exitField();
 * }</pre>
 */
public final class ConfigSource implements StructuredSource {

    private final Map<String, Object> root;
    private final ArrayDeque<String> path = new ArrayDeque<>();

    public ConfigSource(Map<String, Object> data) {
        this.root = data;
    }

    /** Returns the root map (used by {@link SerializableRegistry#readerFor} for reflective construction). */
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

    @Override public boolean readBool()   { return (Boolean) get(); }
    @Override public byte readByte()      { return ((Number) get()).byteValue(); }
    @Override public short readShort()    { return ((Number) get()).shortValue(); }
    @Override public char readChar()      { Object v = get(); return v instanceof Character c ? c : (char)((Number) v).intValue(); }
    @Override public int readInt()        { return ((Number) get()).intValue(); }
    @Override public long readLong()      { return ((Number) get()).longValue(); }
    @Override public float readFloat()    { return ((Number) get()).floatValue(); }
    @Override public double readDouble()  { return ((Number) get()).doubleValue(); }
    @Override public String readString()  { Object v = get(); return v instanceof String s ? s : String.valueOf(v); }
    @Override public UUID readUUID()      { return UUID.fromString(readString()); }
    @Override public byte[] readBytes()   { return (byte[]) get(); }

    @SuppressWarnings("unchecked")
    private Object get() {
        if (path.isEmpty()) {
            throw new IllegalStateException("enterField() required before reading a scalar");
        }
        String[] parts = path.toArray(new String[0]);
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map<?, ?> m)) return null;
            current = (Map<String, Object>) m;
        }
        return current.get(parts[parts.length - 1]);
    }
}
