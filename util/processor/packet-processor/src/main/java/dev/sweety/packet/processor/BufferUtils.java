package dev.sweety.packet.processor;

import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableEncoder;

import java.lang.reflect.InvocationTargetException;

public final class BufferUtils {

    public static <T, Encoder extends CallableEncoder<T>> Encoder encoder(Class<Encoder> encoderClass) {
        try {
            return encoderClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    public static <T, Decoder extends CallableDecoder<T>> Decoder decoder(Class<Decoder> decoderClass) {
        try {
            return decoderClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private BufferUtils() {}

}
