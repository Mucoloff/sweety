package dev.sweety.sql4j.rpc;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;

import java.sql.PreparedStatement;

/**
 * Serializes/deserializes JDBC column values over RPC.
 *
 * <p>Ported from Ecstacy's {@code dev.sweety.sql4j.rpc.RpcCodec}; the wire buffer is luce's
 * {@link PacketBuffer} (VarInt/VarLong length prefixes) instead of sweety's SegmentBuffer. The
 * actual type-tag dispatch (null/boolean/short/int/long/float/double/String/byte[]/UUID/Timestamp/
 * BigDecimal/Map/List) now lives in the shared buffer lib — see {@code AbstractBuffer.writeDynamic}/
 * {@code readDynamic} — this class is just the JDBC-cell entry point into it.
 */
public final class RpcCodec {


    private RpcCodec() {}

    /** Writes a single JDBC cell value to the buffer. */
    public static void write(PacketBuffer buf, Object rawValue) {
        buf.writeDynamic(rawValue);
    }

    /** Reads a single JDBC cell value from the buffer. */
    public static Object read(PacketBuffer buf) {
        return buf.readDynamic();
    }

    /** Serialises a full query (sql + params + returnGeneratedKeys) to bytes. */
    public static byte[] encodeQuery(String sql, Object[] params, boolean returnGeneratedKeys) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            buf.writeString(sql);
            buf.writeBoolean(returnGeneratedKeys);
            int count = params == null ? 0 : params.length;
            buf.writeVarInt(count);
            if (params != null) {
                for (Object p : params) write(buf, p);
            }
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    /**
     * Serialises a row matrix together with its real column names.
     *
     * <p>Wire format: {@code VarInt colCount}, then {@code colCount} column-name strings, then
     * {@code VarInt rowCount}, then the {@code rowCount × colCount} type-tagged cell grid. The
     * column names are carried end-to-end so {@link SyntheticResultSet} can answer
     * label-based lookups ({@code findColumn}/{@code getObject(String)}) — without them, entity
     * mappers that key on {@code ResultSetMetaData.getColumnLabel} bind nothing and hydrate null
     * fields.
     *
     * @param columnNames the real column labels (length defines the column count)
     * @param rows        the row matrix; each row must have {@code columnNames.length} cells
     */
    public static byte[] encodeRows(String[] columnNames, Object[][] rows) {
        int colCount = columnNames == null ? 0 : columnNames.length;
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            buf.writeVarInt(colCount);
            for (int c = 0; c < colCount; c++) {
                buf.writeString(columnNames[c] == null ? "" : columnNames[c]);
            }
            buf.writeVarInt(rows.length);
            for (Object[] row : rows) {
                for (Object cell : row) write(buf, cell);
            }
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    /** Serialises a mutation result to bytes. */
    public static byte[] encodeMutation(int updateCount, long generatedKey) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            buf.writeVarInt(updateCount);
            buf.writeVarLong(generatedKey);
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    /** Decodes a full query from bytes. Returns { sql, params, returnGeneratedKeys }. */
    public static RpcQueryPayload decodeQuery(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            String sql = buf.readString();
            boolean retGenKeys = buf.readBoolean();
            int count = buf.readVarInt();
            Object[] params = new Object[count];
            for (int i = 0; i < count; i++) params[i] = read(buf);
            return new RpcQueryPayload(sql, params, retGenKeys);
        } finally {
            buf.release();
        }
    }

    /** Decodes a row matrix (with its real column names) from bytes. */
    public static RpcRows decodeRows(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            int colCount = buf.readVarInt();
            String[] columns = new String[colCount];
            for (int c = 0; c < colCount; c++) {
                columns[c] = buf.readString();
            }
            int rowCount = buf.readVarInt();
            Object[][] rows = new Object[rowCount][colCount];
            for (int r = 0; r < rowCount; r++) {
                for (int c = 0; c < colCount; c++) {
                    rows[r][c] = read(buf);
                }
            }
            return new RpcRows(columns, rows);
        } finally {
            buf.release();
        }
    }

    /** Decodes a mutation result. */
    public static long[] decodeMutation(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            long updateCount = buf.readVarInt();
            long generatedKey = buf.readVarLong();
            return new long[]{updateCount, generatedKey};
        } finally {
            buf.release();
        }
    }

    /**
     * Serialises a batch mutation (one SQL text, N param rows) to bytes — the wire counterpart
     * of {@link PreparedStatement#addBatch()}/{@link PreparedStatement#executeBatch()}. Column
     * names are re-sent per row in {@link #encodeQuery} for SELECTs; a batch has no result
     * columns so this is just {@code sql + rowCount + rowCount×paramCount cells}, avoiding the
     * per-row {@code sql} + framing overhead of N separate {@link #encodeQuery} calls.
     */
    public static byte[] encodeBatch(String sql, Object[][] paramRows) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            buf.writeString(sql);
            int rowCount = paramRows == null ? 0 : paramRows.length;
            buf.writeVarInt(rowCount);
            if (paramRows != null) {
                for (Object[] row : paramRows) {
                    int paramCount = row == null ? 0 : row.length;
                    buf.writeVarInt(paramCount);
                    if (row != null) {
                        for (Object p : row) write(buf, p);
                    }
                }
            }
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    /** Decodes a batch mutation request. */
    public static RpcBatchPayload decodeBatch(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            String sql = buf.readString();
            int rowCount = buf.readVarInt();
            Object[][] rows = new Object[rowCount][];
            for (int r = 0; r < rowCount; r++) {
                int paramCount = buf.readVarInt();
                Object[] row = new Object[paramCount];
                for (int c = 0; c < paramCount; c++) row[c] = read(buf);
                rows[r] = row;
            }
            return new RpcBatchPayload(sql, rows);
        } finally {
            buf.release();
        }
    }

    /** Serialises a batch result (one JDBC update-count per row, per {@code executeBatch()}). */
    public static byte[] encodeBatchResult(int[] updateCounts) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            int count = updateCounts == null ? 0 : updateCounts.length;
            buf.writeVarInt(count);
            if (updateCounts != null) {
                for (int c : updateCounts) buf.writeVarInt(c);
            }
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    /** Decodes a batch result. */
    public static int[] decodeBatchResult(byte[] data) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer(data.length);
        buf.writeBytes(data);
        try {
            int count = buf.readVarInt();
            int[] result = new int[count];
            for (int i = 0; i < count; i++) result[i] = buf.readVarInt();
            return result;
        } finally {
            buf.release();
        }
    }

    private static byte[] toBytes(PacketBuffer buf) {
        int len = buf.readableBytes();
        byte[] out = new byte[len];
        buf.readBytes(out);
        return out;
    }

    public record RpcQueryPayload(String sql, Object[] params, boolean returnGeneratedKeys) {}

    /** Decoded batch mutation: one SQL text plus N param rows. */
    public record RpcBatchPayload(String sql, Object[][] paramRows) {}

    /** Decoded SELECT result: the real column names plus the row matrix. */
    public record RpcRows(String[] columns, Object[][] rows) {}
}
