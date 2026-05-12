package dev.sweety.data.buffer.io.callable;

/** Combined callable encode/decode helpers (two abstract methods {@link AbstractCallableEncoder#write} / {@link AbstractCallableDecoder#read}). */
public interface AbstractCallableCodec<T> extends AbstractCallableEncoder<T>, AbstractCallableDecoder<T> {
}
