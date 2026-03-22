package dev.sweety.minecraft.nework;

import dev.sweety.minecraft.nework.exception.InvalidPacketDataException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PingUtils {
    public static final int SEGMENT_BITS = 127;
    public static final int CONTINUE_BIT = 128;

    public PingUtils() {
    }

    public static int gobbleVarInt(InputStream is) throws IOException {
        int value = 0;
        int position = 0;

        do {
            int currentByte = is.read();
            if (currentByte == -1) {
                throw new EOFException();
            }

            value |= (currentByte & SEGMENT_BITS) << position;
            if ((currentByte & CONTINUE_BIT) == 0) {
                return value;
            }

            position += 7;
        } while (position < 32);

        throw new InvalidPacketDataException("VarInt is too big", (int[]) null);
    }

    public static long gobbleVarLong(InputStream is) throws IOException {
        long value = 0L;
        int position = 0;

        do {
            int currentByte = is.read();
            if (currentByte == -1) {
                throw new EOFException();
            }

            value |= (long) (currentByte & SEGMENT_BITS) << position;
            if ((currentByte & CONTINUE_BIT) == 0) {
                return value;
            }

            position += 7;
        } while (position < 64);

        throw new InvalidPacketDataException("VarLong is too big", (int[]) null);
    }

    public static void writeVarIntDirectly(OutputStream os, int value) throws IOException {
        while ((value & -CONTINUE_BIT) != 0) {
            os.write(value & SEGMENT_BITS | CONTINUE_BIT);
            value >>>= 7;
        }

        os.write(value);
    }

    public static void writeVarLongDirectly(OutputStream os, long value) throws IOException {
        while ((value & -CONTINUE_BIT) != 0L) {
            os.write((int) (value & SEGMENT_BITS | CONTINUE_BIT));
            value >>>= 7;
        }

        os.write((int) value);
    }
}
