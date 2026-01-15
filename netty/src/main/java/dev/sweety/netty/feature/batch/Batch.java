package dev.sweety.netty.feature.batch;

import dev.sweety.netty.messaging.exception.PacketDecodeException;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;
import dev.sweety.netty.packet.model.Packet;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;

public record Batch(PacketBuffer buffer) {

    public void encode(CallableEncoder<Packet> encode, Packet[] packets) {
        // Framing compatto e senza marker: [count VarInt] + count * [packetBytes VarInt+bytes]
        this.buffer().writeVarInt(packets.length);
        for (Packet packet : packets) {
            PacketBuffer tmp = new PacketBuffer();
            // se il packet è stato decodificato in precedenza, il readerIndex è a fine buffer
            // quindi facciamo rewind() prima di ricodificare.
            encode.write(tmp, packet.rewind());
            this.buffer().writeByteArray(tmp.getBytes());
            tmp.release();
        }
    }

    @SneakyThrows
    public Packet[] decode(CallableDecoder<Packet> decoder) {
        final int count = this.buffer().readVarInt();
        final List<Packet> out = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final byte[] packetBytes = this.buffer().readByteArray();
            final PacketBuffer tmp = new PacketBuffer(packetBytes);
            final Packet packet = decoder.read(tmp);
            tmp.release();

            if (packet == null) {
                throw new PacketDecodeException("Failed to decode packet #" + i + " in batch (bytes=" + packetBytes.length + ")").runtime();
            }
            out.add(packet);
        }

        return out.toArray(Packet[]::new);
    }

}
