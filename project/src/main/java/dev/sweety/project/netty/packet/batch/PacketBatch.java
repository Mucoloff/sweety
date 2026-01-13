package dev.sweety.project.netty.packet.batch;

import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;
import dev.sweety.netty.packet.model.Packet;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;

public class PacketBatch extends Packet {

    public PacketBatch(CallableEncoder<Packet> encode, Packet... packets) {
        super();

        this.buffer().writeVarInt(packets.length);
        for (Packet packet : packets) encode.accept(packet, this.buffer());
    }

    public PacketBatch(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @SneakyThrows
    public List<Packet> decode(CallableDecoder<List<Packet>> decoder){
        List<Packet> read = new ArrayList<>();
        int len = this.buffer().readVarInt();
        for (int i = 0; i < len; i++) {
            read.addAll(decoder.read(this.buffer()));
        }
        return read;
    }
}
