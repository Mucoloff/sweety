package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.BufferReader;

/** Deserializes from a {@link BufferReader} (no concrete buffer type parameter). */
public interface AbstractDecoder {

    void read(BufferReader buffer);

}
