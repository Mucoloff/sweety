package dev.sweety.minecraft.nework.io;

import dev.sweety.minecraft.nework.PingUtils;
import dev.sweety.minecraft.nework.exception.EndOfStreamException;
import dev.sweety.minecraft.nework.exception.InvalidPacketDataException;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PacketReader {

    private final byte[] buffer;
    private int reader = 0;

    public PacketReader(byte[] data) {
        this.buffer = data;
    }

    private static int unpackLongX(long packedPos) {
        return (int) (packedPos >> 38);
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
            this.reader += i;
            if (i < b.length) {
                throw new EndOfStreamException(String.format("Requested %s bytes, read %s bytes", b.length, i));
            }
            return i;
        }
    }

    public String readString() {
        int len = this.readVarInt();
        if (len < 0) {
            throw new InvalidPacketDataException("Negative string length", new int[]{len});
        }
        byte[] buf = new byte[len];
        this.readBytes(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }

    public boolean readBoolean() {
        int i = this.readByte();
        if (i != 0 && i != 1) throw new InvalidPacketDataException("Expected 0x00 or 0x01", new int[]{i});
        return i != 0;
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
        byte[] longBuffer = new byte[8];
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

        while (position < 32) {
            int currentByte = this.readByte();
            value |= (currentByte & PingUtils.SEGMENT_BITS) << position;
            if ((currentByte & PingUtils.CONTINUE_BIT) == 0) {
                return value;
            }
            position += 7;
        }

        throw new InvalidPacketDataException("VarInt too big", null);
    }

    public long readVarLong() {
        long value = 0L;
        int position = 0;

        while (position < 64) {
            int currentByte = this.readByte();
            value |= (long) (currentByte & PingUtils.SEGMENT_BITS) << position;
            if ((currentByte & PingUtils.CONTINUE_BIT) == 0) {
                return value;
            }
            position += 7;
        }

        throw new InvalidPacketDataException("VarLong too big", null);
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
        //noinspection unchecked
        T[] ret = (T[]) Array.newInstance(type, i);

        for (int i1 = 0; i1 < i; ++i1) {
            ret[i1] = converter.apply(this);
        }

        return ret;
    }
}
