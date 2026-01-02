package dev.sweety.minecraft.ping;

import dev.sweety.minecraft.ping.exception.InvalidPacketDataException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PingUtils {
    private static final int SEGMENT_BITS = 127;
    private static final int CONTINUE_BIT = 128;

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

            value |= (currentByte & 127) << position;
            if ((currentByte & 128) == 0) {
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

            value |= (long) (currentByte & 127) << position;
            if ((currentByte & 128) == 0) {
                return value;
            }

            position += 7;
        } while (position < 64);

        throw new InvalidPacketDataException("VarLong is too big", (int[]) null);
    }

    public static void writeVarIntDirectly(OutputStream os, int value) throws IOException {
        while ((value & -128) != 0) {
            os.write(value & 127 | 128);
            value >>>= 7;
        }

        os.write(value);
    }

    public static void writeVarLongDirectly(OutputStream os, long value) throws IOException {
        while ((value & -128L) != 0L) {
            os.write((int) (value & 127L | 128L));
            value >>>= 7;
        }

        os.write((int) value);
    }
}
