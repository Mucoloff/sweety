package dev.sweety.minecraft.ping.io;

import dev.sweety.minecraft.ping.exception.EndOfStreamException;
import dev.sweety.minecraft.ping.exception.InvalidPacketDataException;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PacketReader {
    public static final int SIZE_BITS_Y = 12;
    private static final int SEGMENT_BITS = 127;
    private static final int CONTINUE_BIT = 128;
    private static final byte[] longBuffer = new byte[8];
    private static final int[] varDataBuffer = new int[8];
    private static final int SIZE_BITS_X = 26;
    private static final int SIZE_BITS_Z = 26;
    private static final int BIT_SHIFT_Z = 12;
    private static final int BIT_SHIFT_X = 38;
    private static int varLongWriter = 0;
    private final byte[] buffer;
    private int reader = 0;

    public PacketReader(byte[] data) {
        this.buffer = data;
    }

    private static void resetVarBuffer() {
        Arrays.fill(varDataBuffer, 0);
        varLongWriter = 0;
    }

    private static int unpackLongX(long packedPos) {
        return (int) (packedPos << 0 >> 38);
    }

    private static int unpackLongY(long packedPos) {
        return (int) (packedPos << 52 >> 52);
    }

    private static int unpackLongZ(long packedPos) {
        return (int) (packedPos << 26 >> 38);
    }

    public int getRemaining() {
        return this.buffer.length - this.reader;
    }

    public void reset() {
        this.reader = 0;
    }

    public String toString() {
        int[] mp = new int[this.buffer.length];

        for (int i = 0; i < this.buffer.length; ++i) {
            mp[i] = Byte.toUnsignedInt(this.buffer[i]);
        }

        return String.format("%s{reader=%s,buffer=%s}", this.getClass().getSimpleName(), this.reader, Arrays.stream(mp).mapToObj((value) -> String.format("%02X", value)).collect(Collectors.joining(" ")));
    }

    public int readByte() {
        if (this.reader >= this.buffer.length) {
            throw new EndOfStreamException(String.format("Index %s out of buffer of size %s", this.reader, this.buffer.length));
        } else {
            return Byte.toUnsignedInt(this.buffer[this.reader++]);
        }
    }

    public int readBytes(byte[] b) {
        if (this.reader >= this.buffer.length) {
            throw new EndOfStreamException(String.format("Index %s outside of buffer of size %s", this.reader, this.buffer.length));
        } else {
            int i = Math.min(this.buffer.length - this.reader, b.length);
            System.arraycopy(this.buffer, this.reader, b, 0, i);
            this.reader += b.length;
            return i;
        }
    }

    public String readString() {
        int len = this.readVarInt();
        byte[] buf = new byte[len];
        this.readBytes(buf);
        return new String(buf);
    }

    public boolean readBoolean() {
        int i = this.readByte();
        if (i != 0 && i != 1) {
            throw new InvalidPacketDataException("Expected 0x00 or 0x01", new int[]{i});
        } else {
            return i != 0;
        }
    }

    public short readSignedShort() {
        int ch1 = this.readByte();
        int ch2 = this.readByte();
        return (short) (ch1 << 8 | ch2);
    }

    public int readUnsignedShort() {
        int ch1 = this.readByte();
        int ch2 = this.readByte();
        return ch1 << 8 | ch2;
    }

    public int readInt() {
        int ch1 = this.readByte();
        int ch2 = this.readByte();
        int ch3 = this.readByte();
        int ch4 = this.readByte();
        return ch1 << 24 | ch2 << 16 | ch3 << 8 | ch4;
    }

    public long readLong() {
        this.readBytes(longBuffer);
        return (long) longBuffer[0] << 56 | (long) (longBuffer[1] & 255) << 48 | (long) (longBuffer[2] & 255) << 40 | (long) (longBuffer[3] & 255) << 32 | (long) (longBuffer[4] & 255) << 24 | (long) ((longBuffer[5] & 255) << 16) | (long) ((longBuffer[6] & 255) << 8) | (long) (longBuffer[7] & 255);
    }

    public final float readFloat() {
        return Float.intBitsToFloat(this.readInt());
    }

    public final double readDouble() {
        return Double.longBitsToDouble(this.readLong());
    }

    public int readVarInt() {
        int value = 0;
        int position = 0;
        resetVarBuffer();

        do {
            int currentByte = this.readByte();
            varDataBuffer[varLongWriter++] = currentByte;
            value |= (currentByte & 127) << position;
            if ((currentByte & 128) == 0) {
                return value;
            }

            position += 7;
        } while (position < 32);

        throw new InvalidPacketDataException("VarInt too big", varDataBuffer);
    }

    public long readVarLong() {
        long value = 0L;
        int position = 0;
        resetVarBuffer();

        do {
            int currentByte = this.readByte();
            varDataBuffer[varLongWriter++] = currentByte;
            value |= (long) (currentByte & 127) << position;
            if ((currentByte & 128) == 0) {
                return value;
            }

            position += 7;
        } while (position < 64);

        throw new InvalidPacketDataException("VarLong too big", varDataBuffer);
    }

    public int[] readBlockPos() {
        long l = this.readLong();
        return new int[]{unpackLongX(l), unpackLongY(l), unpackLongZ(l)};
    }

    public UUID readUUID() {
        return new UUID(this.readLong(), this.readLong());
    }

    public <T> T[] readArray(Class<T> type, Function<PacketReader, T> converter) {
        int i = this.readVarInt();
        T[] ret = (T[]) Array.newInstance(type, i);

        for (int i1 = 0; i1 < i; ++i1) {
            ret[i1] = converter.apply(this);
        }

        return ret;
    }
}
