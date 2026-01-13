package dev.sweety.project.test;

import dev.sweety.netty.messaging.listener.decoder.PacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.PacketEncoder;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.project.netty.packet.batch.PacketBatch;
import dev.sweety.project.netty.packet.text.TextPacket;

import java.util.List;

public class TestCodec {

    public static void main(String[] args) throws Throwable {

        final IPacketRegistry registry = new OptimizedPacketRegistry(TextPacket.class, PacketBatch.class);

        final PacketEncoder encoder = new PacketEncoder(registry);
        final PacketDecoder decoder = new PacketDecoder(registry);

        final PacketBuffer out = new PacketBuffer();
        encoder.encode(new PacketBatch(encoder::sneakyEncode, new TextPacket("aaa"), new TextPacket("bbb")), out);

        final PacketBuffer in = new PacketBuffer(out.nettyBuffer());

        List<Packet> packets = new java.util.ArrayList<>();
        decoder.decode(in, packets);

        // Ricostruzione dei singoli pacchetti dal batch
        List<Packet> reconstructed = new java.util.ArrayList<>();
        for (Packet packet : packets) {
            if (packet instanceof PacketBatch batch) {
                List<Packet> decode = batch.decode(decoder::sneakyDecode);
                reconstructed.addAll(decode);
            }
        }

        for (Packet p : reconstructed) {
            System.out.println(p.toString());
            if (p instanceof TextPacket t) {
                // Se TextPacket ha un buffer strutturato, leggere la stringa
                PacketBuffer buf = t.rewind().buffer();
                String s = buf.readString();
                System.out.println("payload: " + s);
            }
        }
    }

}
