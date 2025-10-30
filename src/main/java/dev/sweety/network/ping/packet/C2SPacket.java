package dev.sweety.network.ping.packet;

import dev.sweety.network.ping.io.PacketWriter;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import java.util.zip.Deflater;

public class C2SPacket {
    private final int packetId;
    private final PacketWriter writer;

    public C2SPacket(int packetId) {
        this.packetId = packetId;
        this.writer = new PacketWriter();
    }

    public C2SPacket(int packetId, Consumer<PacketWriter> packetConstructor) {
        this(packetId);
        packetConstructor.accept(this.writer);
    }

    private static byte[] compress(byte[] in) {
        Deflater d = new Deflater();
        d.setInput(in);
        d.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[128];

        while (!d.finished()) {
            int c = d.deflate(buffer);
            baos.write(buffer, 0, c);
        }

        return baos.toByteArray();
    }

    public PacketWriter getWriter() {
        return this.writer;
    }

    public int getPacketId() {
        return this.packetId;
    }

    public byte[] toRawUncompressed() {
        PacketWriter directWriter = new PacketWriter();
        directWriter.writeVarInt(this.packetId);
        directWriter.writeBytes(this.writer.getBuffer());
        directWriter.insertVarInt(0, directWriter.getBuffer().length);
        return directWriter.getBuffer();
    }

    public byte[] toRawCompressed() {
        PacketWriter finalWriter = new PacketWriter();
        PacketWriter directWriter = new PacketWriter();
        directWriter.writeVarInt(this.packetId);
        finalWriter.writeBytes(compress(directWriter.getBuffer()));
        finalWriter.writeBytes(compress(this.writer.getBuffer()));
        finalWriter.insertVarInt(0, directWriter.getBuffer().length + this.writer.getBuffer().length);
        finalWriter.insertVarInt(0, finalWriter.getBuffer().length);
        return finalWriter.getBuffer();
    }

    public String toString() {
        return String.format("%s{id=0x%02X,writer=%s}", this.getClass().getSimpleName(), this.packetId, this.writer);
    }
}
