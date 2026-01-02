package dev.sweety.minecraft.ping.packet;

import dev.sweety.minecraft.ping.PingUtils;
import dev.sweety.minecraft.ping.io.PacketReader;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class S2CPacket {
    private final PacketReader pr;
    private final int id;

    public S2CPacket(PacketReader pr) {
        int i = pr.readVarInt();
        byte[] b = new byte[pr.getRemaining()];
        pr.readBytes(b);
        this.pr = new PacketReader(b);
        this.id = i;
    }

    public S2CPacket(int id, byte[] data) {
        this.pr = new PacketReader(data);
        this.id = id;
    }

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
            Inflater i = new Inflater();
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

    public int getId() {
        return this.id;
    }

    public PacketReader getPacketReader() {
        return this.pr;
    }

    public String toString() {
        return String.format("%s{id=0x%02X,data=%s}", this.getClass().getSimpleName(), this.id, this.pr.toString());
    }
}
