package dev.sweety.config.common.serialization;

import dev.sweety.config.common.ConfigurationSection;

import java.util.Map;
import java.util.TreeMap;

import dev.sweety.data.buffer.NioBuffer;
import dev.sweety.serialization.format.StructuredSink;

public interface ConfigSerializable {

    @Deprecated
    default Map<String, Object> serialize() {
        Map<String, Object> map = new TreeMap<>();
        serialize(ConfigurationSection.fromMap(map));
        return map;
    }

    default void serialize(ConfigurationSection section) {
        Map<String, Object> map = serialize();
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                section.set(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Writes this object into the given sink at the current field path.
     */
    default void write(ConfigSink sink) {
        Map<String, Object> map = new TreeMap<>();
        serialize(ConfigurationSection.fromMap(map));
        sink.writeRawMap(map);
    }

    /**
     * Serializes this object directly into any format-agnostic {@link StructuredSink}
     * (including {@link dev.sweety.data.buffer.AbstractBuffer} and {@link NioBuffer}).
     */
    default void write(StructuredSink sink) {
        if (sink instanceof ConfigSink configSink) {
            write(configSink);
            return;
        }
        Map<String, Object> map = new TreeMap<>();
        serialize(ConfigurationSection.fromMap(map));
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sink.enterField(entry.getKey());
            writeToSink(sink, entry.getValue());
            sink.exitField();
        }
    }

    private static void writeToSink(StructuredSink sink, Object value) {
        switch (value) {
            case Boolean b -> sink.writeBool(b);
            case Byte b -> sink.writeByte(b);
            case Short s -> sink.writeShort(s);
            case Character c -> sink.writeChar(c);
            case Integer i -> sink.writeInt(i);
            case Long l -> sink.writeLong(l);
            case Float f -> sink.writeFloat(f);
            case Double d -> sink.writeDouble(d);
            case String s -> sink.writeString(s);
            case java.util.UUID u -> sink.writeUUID(u);
            case byte[] bytes -> sink.writeBytes(bytes);
            case ConfigSerializable cs -> cs.write(sink);
            case Map<?, ?> m -> {
                sink.writeInt(m.size());
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    sink.writeString(String.valueOf(e.getKey()));
                    writeToSink(sink, e.getValue());
                }
            }
            case java.util.Collection<?> col -> {
                sink.writeInt(col.size());
                for (Object item : col) {
                    writeToSink(sink, item);
                }
            }
            case null, default -> {
                if (value != null) sink.writeString(value.toString());
            }
        }
    }

    /**
     * Serializes this object directly into a newly allocated {@link NioBuffer}.
     */
    default NioBuffer toBuffer() {
        NioBuffer buf = NioBuffer.heap();
        write(buf.asSink());
        return buf;
    }
}

