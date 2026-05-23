package dev.sweety.serialization.format;

import dev.sweety.serialization.Reader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Format-agnostic read surface, mirroring {@link StructuredSink}.
 */
public interface StructuredSource {

    boolean readBool();
    byte readByte();
    short readShort();
    char readChar();
    int readInt();
    long readLong();
    float readFloat();
    double readDouble();
    String readString();
    UUID readUUID();
    byte[] readBytes();

    void enterField(String name);
    void exitField();

    default <E, C extends Collection<E>> C readCollection(
            Reader<? extends E, StructuredSource> r,
            IntFunction<C> factory) {
        int size = readInt();
        C result = factory.apply(size);
        for (int i = 0; i < size; i++) result.add(r.read(this));
        return result;
    }

    default <E> List<E> readList(Reader<? extends E, StructuredSource> r) {
        return readCollection(r, ArrayList::new);
    }

    default <K, V> Map<K, V> readMap(
            Reader<? extends K, StructuredSource> kr,
            Reader<? extends V, StructuredSource> vr,
            IntFunction<Map<K, V>> factory) {
        int size = readInt();
        Map<K, V> result = factory.apply(size);
        for (int i = 0; i < size; i++) result.put(kr.read(this), vr.read(this));
        return result;
    }

    default <E> Optional<E> readOptional(Reader<? extends E, StructuredSource> r) {
        return readBool() ? Optional.of(r.read(this)) : Optional.empty();
    }

    default <E extends Enum<E>> E readEnum(Class<E> clazz) {
        return Enum.valueOf(clazz, readString());
    }
}
