package dev.sweety.serialization.format;

import dev.sweety.serialization.Writer;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Format-agnostic write surface. Implementations bridge to concrete formats (binary buffer, config map, …).
 * {@code enterField}/{@code exitField} are no-ops for flat binary formats; config implementations use them
 * to maintain a dotted-path stack.
 */
public interface StructuredSink {

    void writeBool(boolean v);
    void writeByte(byte v);
    void writeShort(short v);
    void writeChar(char v);
    void writeInt(int v);
    void writeLong(long v);
    void writeFloat(float v);
    void writeDouble(double v);
    void writeString(String v);
    void writeUUID(UUID v);
    void writeBytes(byte[] v);

    void enterField(String name);
    void exitField();

    default <E> void writeCollection(Collection<E> c, Writer<? super E, StructuredSink> w) {
        writeInt(c.size());
        for (E e : c) w.write(this, e);
    }

    default <K, V> void writeMap(Map<K, V> m,
                                  Writer<? super K, StructuredSink> kw,
                                  Writer<? super V, StructuredSink> vw) {
        writeInt(m.size());
        for (Map.Entry<K, V> entry : m.entrySet()) {
            kw.write(this, entry.getKey());
            vw.write(this, entry.getValue());
        }
    }

    default <E> void writeOptional(Optional<E> o, Writer<? super E, StructuredSink> w) {
        writeBool(o.isPresent());
        o.ifPresent(e -> w.write(this, e));
    }

    default <E extends Enum<E>> void writeEnum(E v) {
        writeString(v.name());
    }
}
