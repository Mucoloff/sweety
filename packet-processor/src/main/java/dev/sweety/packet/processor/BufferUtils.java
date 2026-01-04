package dev.sweety.packet.processor;

import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BufferUtils {

    @SneakyThrows
    public <T, Encoder extends CallableEncoder<T>> Encoder encoder(Class<Encoder> encoderClass) {
        return encoderClass.getDeclaredConstructor().newInstance();
    }

    @SneakyThrows
    public <T, Decoder extends CallableDecoder<T>> Decoder decoder(Class<Decoder> decoderClass) {
        return decoderClass.getDeclaredConstructor().newInstance();
    }

}
