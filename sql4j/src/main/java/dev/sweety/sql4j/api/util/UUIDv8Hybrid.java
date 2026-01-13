package dev.sweety.sql4j.api.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class UUIDv8Hybrid {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final File COUNTER_FILE = new File("uuid-v8.dat");
    private static final AtomicLong COUNTER = new AtomicLong();
    private static final long NODE_ID = RANDOM.nextLong();

    static {
        if (COUNTER_FILE.exists()) {
            try (DataInputStream dis = new DataInputStream(new FileInputStream(COUNTER_FILE))) {
                COUNTER.set(readVarLong(dis));
            } catch (IOException e) {
                COUNTER.set(0);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(COUNTER_FILE))) {
                writeVarLong(dos, COUNTER.get());
            } catch (IOException ignored) {
            }
        }));
    }

    public static UUID generate(long timestamp) {
        final long mostSig = ((((timestamp & 0xFFFFFFFFL) << 32) | (((timestamp >> 32) & 0xFFFFL) << 16) | ((timestamp >> 48) & 0x0FFFL)) & ~(0xF000L)) | 0x1000L;
        final UUID uuid = UUID.randomUUID();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] lsbBytes = new byte[8];

        try {
            writeVarLong(out, COUNTER.getAndIncrement() ^ RANDOM.nextLong() ^ uuid.getMostSignificantBits());
            writeVarLong(out, NODE_ID ^ RANDOM.nextLong() ^ uuid.getLeastSignificantBits());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final byte[] data = out.toByteArray();
        for (int i = 0; i < 8; i++) lsbBytes[i] = i < data.length ? data[i] : (byte) (RANDOM.nextInt() ^ 0x8);

        final long leastSig = (ByteBuffer.wrap(lsbBytes).getLong() & ~(0xC000000000000000L)) | 0x8000000000000000L;
        return new UUID(mostSig, leastSig);
    }


    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    public static byte[] toBytes(UUID uuid) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    public static void writeVarLong(OutputStream out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.write((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }

    public static long readVarLong(InputStream in) throws IOException {
        int numRead = 0;
        long result = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) throw new EOFException();
            long value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 10) throw new IOException("VarLong too big");
        } while ((read & 0x80) != 0);
        return result;
    }

}
