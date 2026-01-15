package dev.sweety.netty.feature.batch;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;
import dev.sweety.netty.packet.model.Packet;
import lombok.SneakyThrows;

public record Batch(PacketBuffer buffer) {

    public void encode(CallableEncoder<Packet> encode, Packet[] packets) {
        this.buffer().writeArray(encode, packets);
    }

    @SneakyThrows
    public Packet[] decode(CallableDecoder<Packet> decoder) {
        return this.buffer().readArray(decoder, Packet[]::new);
    }

}
