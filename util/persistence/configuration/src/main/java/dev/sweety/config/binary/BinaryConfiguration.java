package dev.sweety.config.binary;

import dev.sweety.config.common.Configuration;
import dev.sweety.data.buffer.NioBuffer;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * High-performance binary configuration format 100% aligned with {@link dev.sweety.data.buffer.AbstractBuffer}.
 * Uses writeDynamic/readDynamic with compact VarInts, VarLongs, bit-packed booleans, and UTF-8 zero-alloc strings.
 */
public class BinaryConfiguration extends Configuration {

    public static final String MAGIC_V2 = "CFG2";
    public static final String MAGIC_V1 = "CFG1";
    public static final byte VERSION_2 = 2;
    public static final byte VERSION_1 = 1;

    private final String magic;
    private final byte version;

    public BinaryConfiguration() {
        this("bin", MAGIC_V2, VERSION_2);
    }

    public BinaryConfiguration(String extension, String magic, int version) {
        super(extension);
        this.magic = magic;
        this.version = (byte) version;
    }

    @Override
    protected void dumpToStream(Map<String, Object> map, OutputStream out) throws IOException {
        final NioBuffer buffer = NioBuffer.heap(1024);
        try {
            // Header: 4-byte ASCII magic + 1-byte version
            buffer.writeBytes(this.magic.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            buffer.writeByte(this.version);

            buffer.writeDynamic(map);

            out.write(buffer.readAllBytes());
            out.flush();
        } finally {
            buffer.release();
        }
    }

    @Override
    protected Map<String, Object> loadFromStream(InputStream in) throws IOException {
        byte[] allBytes = in.readAllBytes();
        if (allBytes.length < 5) {
            throw new IllegalStateException("Binary config stream too short");
        }

        String readMagic = new String(allBytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        byte readVersion = allBytes[4];

        if (MAGIC_V1.equals(readMagic) && readVersion == VERSION_1) {
            // Backward compatibility fallback for legacy CFG1 format
            return loadLegacyV1(allBytes);
        }

        if (!this.magic.equals(readMagic)) {
            throw new IllegalStateException("Invalid binary config magic: " + readMagic + ", expected: " + this.magic);
        }
        if (readVersion != this.version) {
            throw new IllegalStateException("Unsupported binary config version: " + readVersion + ", expected: " + this.version);
        }

        NioBuffer buffer = NioBuffer.wrap(allBytes);
        try {
            buffer.readerIndex(5); // skip 4 magic + 1 version
            Object root = buffer.readDynamic();
            if (!(root instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Root must be a Map, found: " + (root != null ? root.getClass() : "null"));
            }
            //noinspection unchecked
            return (Map<String, Object>) map;
        } finally {
            buffer.release();
        }
    }

    // ── Legacy V1 Compatibility Fallback ──────────────────────────────────────

    private Map<String, Object> loadLegacyV1(byte[] allBytes) throws IOException {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(allBytes));
        byte[] magic = new byte[4];
        data.readFully(magic);
        byte version = data.readByte();

        Object legacyRoot = readLegacyObject(data);
        if (!(legacyRoot instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Legacy Root must be a Map");
        }
        //noinspection unchecked
        return (Map<String, Object>) map;
    }

    private Object readLegacyObject(DataInputStream in) throws IOException {
        byte type = in.readByte();
        return switch (type) {
            case 0 -> null;
            case 1 -> in.readUTF();
            case 2 -> in.readInt();
            case 3 -> in.readLong();
            case 4 -> in.readDouble();
            case 5 -> in.readBoolean();
            case 6 -> readLegacyList(in);
            case 7 -> readLegacyMap(in);
            case 8 -> in.readFloat();
            case 9 -> in.readByte();
            case 10 -> in.readShort();
            case 11 -> in.readChar();
            default -> throw new IllegalStateException("Unknown legacy type: " + type);
        };
    }

    private Map<String, Object> readLegacyMap(DataInputStream in) throws IOException {
        int size = in.readInt();
        Map<String, Object> map = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            Object value = readLegacyObject(in);
            map.put(key, value);
        }
        return map;
    }

    private List<Object> readLegacyList(DataInputStream in) throws IOException {
        int size = in.readInt();
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(readLegacyObject(in));
        }
        return list;
    }
}
