package dev.sweety.network.ping.io;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PacketWriter {
    public static final int SIZE_BITS_Y = 12;
    private static final int SEGMENT_BITS = 127;
    private static final int CONTINUE_BIT = 128;
    private static final int SIZE_BITS_X = 26;
    private static final int SIZE_BITS_Z = 26;
    private static final long BITS_X = 67108863L;
    private static final long BITS_Y = 4095L;
    private static final long BITS_Z = 67108863L;
    private static final int BIT_SHIFT_Z = 12;
    private static final int BIT_SHIFT_X = 38;
    private final byte[] writeBuffer = new byte[1];
    int writer = 0;
    private byte[] buffer = new byte[0];

    public PacketWriter() {
    }

    public void reset() {
        this.writer = 0;
        this.buffer = new byte[0];
    }

    private void grow(int i) {
        int existingLength = this.buffer.length;
        byte[] newBuffer = new byte[existingLength + i];
        System.arraycopy(this.buffer, 0, newBuffer, 0, this.buffer.length);
        this.buffer = newBuffer;
    }

    public PacketWriter writeByte(int i) {
        this.writeBuffer[0] = (byte) i;
        this.writeBytes(this.writeBuffer);
        return this;
    }

    public PacketWriter insertBytes(int index, byte... b) {
        if (index >= this.buffer.length) {
            throw new IndexOutOfBoundsException(index);
        } else {
            this.grow(b.length);
            byte[] f = new byte[this.buffer.length];
            int i = 0;
            System.arraycopy(this.buffer, 0, f, 0, index);
            i += index;
            System.arraycopy(b, 0, f, i, b.length);
            i += b.length;
            System.arraycopy(this.buffer, index, f, i, this.buffer.length - i);
            this.buffer = f;
            return this;
        }
    }

    public PacketWriter writeBytes(byte... b) {
        this.grow(b.length);
        System.arraycopy(b, 0, this.buffer, this.writer, b.length);
        this.writer += b.length;
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
        while ((value & -128) != 0) {
            this.writeByte(value & 127 | 128);
            value >>>= 7;
        }

        this.writeByte(value);
        return this;
    }

    public PacketWriter insertVarInt(int i, int value) {
        int ind;
        for (ind = 0; (value & -128) != 0; value >>>= 7) {
            this.insertBytes(i + ind, (byte) (value & 127 | 128));
            ++ind;
        }

        this.insertBytes(i + ind, (byte) value);
        return this;
    }

    public PacketWriter writeVarLong(long value) {
        while ((value & -128L) != 0L) {
            this.writeByte((int) (value & 127L | 128L));
            value >>>= 7;
        }

        this.writeByte((int) value);
        return this;
    }

    public PacketWriter writeString(String s) {
        this.writeVarInt(s.length());
        this.writeBytes(s.getBytes(StandardCharsets.UTF_8));
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

    public byte[] getBuffer() {
        return this.buffer;
    }
}
