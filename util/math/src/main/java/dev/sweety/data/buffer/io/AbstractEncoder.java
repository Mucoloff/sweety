package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.BufferWriter;
/** Serializes into a {@link dev.sweety.data.buffer.BufferWriter} (no concrete buffer type parameter). */
public interface AbstractEncoder {

    void write(BufferWriter buffer);

}
