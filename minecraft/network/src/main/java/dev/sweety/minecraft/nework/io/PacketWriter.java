package dev.sweety.minecraft.nework.io;

import dev.sweety.minecraft.nework.PingUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PacketWriter {
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public PacketWriter() {
    }

    public void reset() {
        this.buffer.reset();
    }

    public byte[] getBuffer() {
        return this.buffer.toByteArray();
    }

    public PacketWriter writeByte(int i) {
        this.buffer.write(i);
        return this;
    }

    public PacketWriter insertBytes(int index, byte... b) {
        byte[] current = this.buffer.toByteArray();
        if (index > current.length || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + current.length);
        }

        ByteArrayOutputStream newBuffer = new ByteArrayOutputStream(current.length + b.length);
        newBuffer.write(current, 0, index);
        try {
            newBuffer.write(b);
            newBuffer.write(current, index, current.length - index);
        } catch (IOException e) {
            // Should not happen with ByteArrayOutputStream
            throw new RuntimeException(e);
        }
        this.buffer = newBuffer;
        return this;
    }

    public PacketWriter writeBytes(byte... b) {
        try {
            this.buffer.write(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }


    public PacketWriter writeBoolean(boolean b) {
        this.writeByte(b ? 1 : 0);
        return this;
    }

    public PacketWriter writeShort(int v) {
        this.writeBytes((byte) (v >>> 8 & 255), (byte) (v & 255));
        return this;
    }

    public PacketWriter writeInt(int v) {
        this.writeBytes((byte) (v >>> 24 & 255), (byte) (v >>> 16 & 255), (byte) (v >>> 8 & 255), (byte) (v & 255));
        return this;
    }

    public PacketWriter writeLong(long v) {
        this.writeBytes((byte) ((int) (v >>> 56 & 255L)), (byte) ((int) (v >>> 48 & 255L)), (byte) ((int) (v >>> 40 & 255L)), (byte) ((int) (v >>> 32 & 255L)), (byte) ((int) (v >>> 24 & 255L)), (byte) ((int) (v >>> 16 & 255L)), (byte) ((int) (v >>> 8 & 255L)), (byte) ((int) (v & 255L)));
        return this;
    }

    public PacketWriter writeFloat(float f) {
        this.writeInt(Float.floatToRawIntBits(f));
        return this;
    }

    public PacketWriter writeDouble(double d) {
        this.writeLong(Double.doubleToRawLongBits(d));
        return this;
    }

    public PacketWriter writeVarInt(int value) {
        while ((value & -PingUtils.CONTINUE_BIT) != 0) {
            this.writeByte(value & PingUtils.SEGMENT_BITS | PingUtils.CONTINUE_BIT);
            value >>>= 7;
        }

        this.writeByte(value);
        return this;
    }

    public PacketWriter insertVarInt(int index, int value) {
        ByteArrayOutputStream temp = new ByteArrayOutputStream(5);
        while ((value & -PingUtils.CONTINUE_BIT) != 0) {
            temp.write((byte) (value & PingUtils.SEGMENT_BITS | PingUtils.CONTINUE_BIT));
            value >>>= 7;
        }
        temp.write((byte) value);
        return this.insertBytes(index, temp.toByteArray());
    }

    public PacketWriter writeVarLong(long value) {
        while ((value & -PingUtils.CONTINUE_BIT) != 0L) {
            this.writeByte((int) (value & PingUtils.SEGMENT_BITS | PingUtils.CONTINUE_BIT));
            value >>>= 7;
        }

        this.writeByte((int) value);
        return this;
    }

    public PacketWriter writeString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        this.writeVarInt(bytes.length);
        this.writeBytes(bytes);
        return this;
    }

    public PacketWriter writeBlockPosition(int x, int y, int z) {
        long l = 0L;
        l |= ((long) x & 67108863L) << 38;
        l |= (long) y & 4095L;
        l |= ((long) z & 67108863L) << 12;
        this.writeLong(l);
        return this;
    }

    public PacketWriter writeUUID(UUID u) {
        this.writeLong(u.getMostSignificantBits());
        this.writeLong(u.getLeastSignificantBits());
        return this;
    }

}
