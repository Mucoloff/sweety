package dev.sweety.netty.feature.batch;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;
import dev.sweety.netty.packet.model.Packet;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class Batch {

    public void encode(final PacketBuffer buffer, final CallableEncoder<Packet> encoder, final Packet... packets) {
        buffer.writeArray(encoder, packets);
    }

    @SneakyThrows
    public Packet[] decode(final PacketBuffer buffer,final CallableDecoder<Packet> decoder) {
        return buffer.readArray(decoder, Packet[]::new);
    }

}
