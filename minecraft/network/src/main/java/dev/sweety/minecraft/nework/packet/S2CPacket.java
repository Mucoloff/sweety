package dev.sweety.minecraft.nework.packet;

import dev.sweety.minecraft.nework.PingUtils;
import dev.sweety.minecraft.nework.io.PacketReader;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public record S2CPacket(int id, PacketReader packetReader) {

    public S2CPacket(int id, byte[] data) {
        this(id, new PacketReader(data));
    }

    public S2CPacket(PacketReader packetReader) {
        this(packetReader.readVarInt(), f(packetReader));
    }

    private static PacketReader f(PacketReader reader) {
        byte[] b = new byte[reader.getRemaining()];
        reader.readBytes(b);
        return new PacketReader(b);
    }

    private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);

    public static S2CPacket readCompressedFromStream(InputStream is) throws IOException, DataFormatException {
        DataInputStream dis = new DataInputStream(is);
        int topBufferLen = PingUtils.gobbleVarInt(dis);
        byte[] topBuffer = new byte[topBufferLen];
        dis.readFully(topBuffer);
        PacketReader pr = new PacketReader(topBuffer);
        int decompressedLength = pr.readVarInt();
        if (decompressedLength == 0) {
            return new S2CPacket(pr);
        } else {
            byte[] compressed = new byte[pr.getRemaining()];
            pr.readBytes(compressed);
            Inflater i = INFLATER.get();
            i.reset();
            i.setInput(compressed);
            byte[] decompressed = new byte[decompressedLength];
            i.inflate(decompressed);
            return new S2CPacket(new PacketReader(decompressed));
        }
    }

    public static S2CPacket readUncompressedFromStream(InputStream is) throws IOException {
        DataInputStream d = new DataInputStream(is);
        int length = PingUtils.gobbleVarInt(d);
        byte[] buffer = new byte[length];
        d.readFully(buffer);
        return new S2CPacket(new PacketReader(buffer));
    }

    public String toString() {
        return String.format("%s{id=0x%02X,data=%s}", this.getClass().getSimpleName(), this.id, this.packetReader.toString());
    }
}
