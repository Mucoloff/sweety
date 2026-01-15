package dev.sweety.netty.feature.batch;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;
import dev.sweety.netty.packet.model.Packet;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;

public record Batch(PacketBuffer buffer) {

    public void encode(CallableEncoder<Packet> encode, Packet[] packets) {
        this.buffer().writeVarInt(packets.length);
        for (Packet packet : packets) encode.accept(packet, this.buffer());
    }

    @SneakyThrows
    public Packet[] decode(CallableDecoder<List<Packet>> decoder) {
        int len = this.buffer().readVarInt();
        List<Packet> read = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            read.addAll(decoder.read(this.buffer()));
        }
        return read.toArray(Packet[]::new);
    }

}
