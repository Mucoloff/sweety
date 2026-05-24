package dev.sweety.sql4j.rpc;

import dev.sweety.data.buffer.SegmentBuffer;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Binary type-tagged codec for serializing/deserializing JDBC column values over RPC.
 *
 * <p>Wire format uses VarInt/VarLong length prefixes (sweety SegmentBuffer defaults).
 *
 * <p>Type tags:
 * <pre>
 *  0 = null
 *  1 = PacketBoolean
 *  2 = VarInt
 *  3 = VarLong
 *  4 = Double
 *  5 = String
 *  6 = byte[]
 *  7 = UUID   (two VarLongs: MSB, LSB)
 *  8 = Timestamp (VarLong millis)
 *  9 = BigDecimal (String representation)
 * 10 = Float
 * 11 = Short
 * </pre>
 */
public final class RpcCodec {

    public static final byte TAG_NULL      = 0;
    public static final byte TAG_BOOLEAN   = 1;
    public static final byte TAG_INTEGER   = 2;
    public static final byte TAG_LONG      = 3;
    public static final byte TAG_DOUBLE    = 4;
    public static final byte TAG_STRING    = 5;
    public static final byte TAG_BYTES     = 6;
    public static final byte TAG_UUID      = 7;
    public static final byte TAG_TIMESTAMP = 8;
    public static final byte TAG_DECIMAL   = 9;
    public static final byte TAG_FLOAT     = 10;
    public static final byte TAG_SHORT     = 11;

    private RpcCodec() {}

    /** Returns the type tag for the given object (must be a JDBC-compatible type or null). */
    public static byte tagOf(Object value) {
        if (value == null)                return TAG_NULL;
        if (value instanceof Boolean)     return TAG_BOOLEAN;
        if (value instanceof Integer)     return TAG_INTEGER;
        if (value instanceof Long)        return TAG_LONG;
        if (value instanceof Double)      return TAG_DOUBLE;
        if (value instanceof String)      return TAG_STRING;
        if (value instanceof byte[])      return TAG_BYTES;
        if (value instanceof UUID)        return TAG_UUID;
        if (value instanceof Timestamp)   return TAG_TIMESTAMP;
        if (value instanceof BigDecimal)  return TAG_DECIMAL;
        if (value instanceof Float)       return TAG_FLOAT;
        if (value instanceof Short)       return TAG_SHORT;
        // Fallback: convert to String
        return TAG_STRING;
    }

    /** Normalises a value before tagging (handles Enum, unexpected types, etc.). */
    public static Object normalise(Object value) {
        if (value == null) return null;
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof Date d) return new Timestamp(d.getTime());
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime());
        // Boolean stored as number (some JDBC drivers)
        if (value instanceof Number && !(value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Float
                || value instanceof Short || value instanceof BigDecimal)) {
            return ((Number) value).longValue();
        }
        return value;
    }

    /** Writes a single value to the buffer. */
    public static void write(SegmentBuffer buf, Object rawValue) {
        Object value = normalise(rawValue);
        byte tag = tagOf(value);
        buf.writeByte(tag);
        switch (tag) {
            case TAG_NULL      -> {}
            case TAG_BOOLEAN   -> buf.writeBoolean(((Boolean) value));
            case TAG_INTEGER   -> buf.writeVarInt((Integer) value);
            case TAG_LONG      -> buf.writeVarLong((Long) value);
            case TAG_DOUBLE    -> buf.writeDouble((Double) value);
            case TAG_STRING    -> buf.writeString(value instanceof String s ? s : value.toString());
            case TAG_BYTES     -> buf.writeByteArray((byte[]) value);
            case TAG_UUID      -> buf.writeUuid((UUID) value);
            case TAG_TIMESTAMP -> buf.writeVarLong(((Timestamp) value).getTime());
            case TAG_DECIMAL   -> buf.writeString(((BigDecimal) value).toPlainString());
            case TAG_FLOAT     -> buf.writeFloat((Float) value);
            case TAG_SHORT     -> buf.writeShort((Short) value);
        }
    }

    /** Reads a single value from the buffer. */
    public static Object read(SegmentBuffer buf) {
        byte tag = buf.readByte();
        return switch (tag) {
            case TAG_NULL      -> null;
            case TAG_BOOLEAN   -> buf.readBoolean();
            case TAG_INTEGER   -> buf.readVarInt();
            case TAG_LONG      -> buf.readVarLong();
            case TAG_DOUBLE    -> buf.readDouble();
            case TAG_STRING    -> buf.readString();
            case TAG_BYTES     -> buf.readByteArray();
            case TAG_UUID      -> buf.readUuid();
            case TAG_TIMESTAMP -> new Timestamp(buf.readVarLong());
            case TAG_DECIMAL   -> new BigDecimal(buf.readString());
            case TAG_FLOAT     -> buf.readFloat();
            case TAG_SHORT     -> buf.readShort();
            default -> throw new IllegalStateException("Unknown RPC type tag: " + tag);
        };
    }

    /** Serialises a full query (sql + params + returnGeneratedKeys) to bytes. */
    public static byte[] encodeQuery(String sql, Object[] params, boolean returnGeneratedKeys) {
        SegmentBuffer buf = new SegmentBuffer();
        buf.writeString(sql);
        buf.writeBoolean(returnGeneratedKeys);
        int count = params == null ? 0 : params.length;
        buf.writeVarInt(count);
        if (params != null) {
            for (Object p : params) write(buf, p);
        }
        byte[] result = buf.getBytes();
        buf.release();
        return result;
    }

    /** Serialises a row matrix (Object[][]) to bytes. */
    public static byte[] encodeRows(Object[][] rows, int colCount) {
        SegmentBuffer buf = new SegmentBuffer();
        buf.writeVarInt(colCount);
        buf.writeVarInt(rows.length);
        for (Object[] row : rows) {
            for (Object cell : row) write(buf, cell);
        }
        byte[] result = buf.getBytes();
        buf.release();
        return result;
    }

    /** Serialises a mutation result to bytes. */
    public static byte[] encodeMutation(int updateCount, long generatedKey) {
        SegmentBuffer buf = new SegmentBuffer();
        buf.writeVarInt(updateCount);
        buf.writeVarLong(generatedKey);
        byte[] result = buf.getBytes();
        buf.release();
        return result;
    }

    /** Decodes a full query from bytes. Returns { sql, params, returnGeneratedKeys }. */
    public static RpcQueryPayload decodeQuery(byte[] data) {
        SegmentBuffer buf = SegmentBuffer.wrap(ByteBuffer.wrap(data));
        String sql = buf.readString();
        boolean retGenKeys = buf.readBoolean();
        int count = buf.readVarInt();
        Object[] params = new Object[count];
        for (int i = 0; i < count; i++) params[i] = read(buf);
        buf.release();
        return new RpcQueryPayload(sql, params, retGenKeys);
    }

    /** Decodes a row matrix from bytes. Returns Object[][] rows. */
    public static Object[][] decodeRows(byte[] data) {
        SegmentBuffer buf = SegmentBuffer.wrap(ByteBuffer.wrap(data));
        int colCount = buf.readVarInt();
        int rowCount = buf.readVarInt();
        Object[][] rows = new Object[rowCount][colCount];
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                rows[r][c] = read(buf);
            }
        }
        buf.release();
        return rows;
    }

    /** Decodes a mutation result. */
    public static long[] decodeMutation(byte[] data) {
        SegmentBuffer buf = SegmentBuffer.wrap(ByteBuffer.wrap(data));
        long updateCount = buf.readVarInt();
        long generatedKey = buf.readVarLong();
        buf.release();
        return new long[]{updateCount, generatedKey};
    }

    public record RpcQueryPayload(String sql, Object[] params, boolean returnGeneratedKeys) {}
}
