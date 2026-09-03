package dev.sweety.sql4j.rpc;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;

import java.sql.PreparedStatement;

/**
 * Serializes/deserializes JDBC column values over RPC with zero-copy stream methods.
 */
public final class RpcCodec {

    private RpcCodec() {}

    /** Writes a single JDBC cell value to any buffer writer. */
    public static void write(BufferWriter buf, Object rawValue) {
        buf.writeDynamic(rawValue);
    }

    /** Reads a single JDBC cell value from any buffer reader. */
    public static Object read(BufferReader buf) {
        return buf.readDynamic();
    }

    // ─── Direct Streaming Methods (Zero-Copy) ────────────────────────────────

    public static void writeQuery(BufferWriter buf, String sql, Object[] params, boolean returnGeneratedKeys) {
        buf.writeString(sql != null ? sql : "");
        buf.writeBoolean(returnGeneratedKeys);
        int count = params == null ? 0 : params.length;
        buf.writeVarInt(count);
        if (params != null) {
            for (Object p : params) write(buf, p);
        }
    }

    public static RpcQueryPayload readQuery(BufferReader buf) {
        String sql = buf.readString();
        boolean retGenKeys = buf.readBoolean();
        int count = buf.readVarInt();
        Object[] params = new Object[count];
        for (int i = 0; i < count; i++) params[i] = read(buf);
        return new RpcQueryPayload(sql, params, retGenKeys);
    }

    public static void writeRows(BufferWriter buf, String[] columnNames, Object[][] rows) {
        int colCount = columnNames == null ? 0 : columnNames.length;
        buf.writeVarInt(colCount);
        for (int c = 0; c < colCount; c++) {
            buf.writeString(columnNames[c] == null ? "" : columnNames[c]);
        }
        int rowCount = rows == null ? 0 : rows.length;
        buf.writeVarInt(rowCount);
        if (rows != null) {
            for (Object[] row : rows) {
                for (Object cell : row) write(buf, cell);
            }
        }
    }

    public static RpcRows readRows(BufferReader buf) {
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
    }

    public static void writeMutation(BufferWriter buf, int updateCount, long generatedKey) {
        buf.writeVarInt(updateCount);
        buf.writeVarLong(generatedKey);
    }

    public static long[] readMutation(BufferReader buf) {
        long updateCount = buf.readVarInt();
        long generatedKey = buf.readVarLong();
        return new long[]{updateCount, generatedKey};
    }

    public static void writeBatch(BufferWriter buf, String sql, Object[][] paramRows) {
        buf.writeString(sql != null ? sql : "");
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
    }

    public static RpcBatchPayload readBatch(BufferReader buf) {
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
    }

    public static void writeBatchResult(BufferWriter buf, int[] updateCounts) {
        int count = updateCounts == null ? 0 : updateCounts.length;
        buf.writeVarInt(count);
        if (updateCounts != null) {
            for (int c : updateCounts) buf.writeVarInt(c);
        }
    }

    public static int[] readBatchResult(BufferReader buf) {
        int count = buf.readVarInt();
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = buf.readVarInt();
        return result;
    }

    // ─── Legacy byte[] Adapters (Full Backward Compatibility) ────────────────

    public static byte[] encodeQuery(String sql, Object[] params, boolean returnGeneratedKeys) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            writeQuery(buf, sql, params, returnGeneratedKeys);
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    public static byte[] encodeRows(String[] columnNames, Object[][] rows) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            writeRows(buf, columnNames, rows);
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    public static byte[] encodeMutation(int updateCount, long generatedKey) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            writeMutation(buf, updateCount, generatedKey);
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    public static RpcQueryPayload decodeQuery(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            return readQuery(buf);
        } finally {
            buf.release();
        }
    }

    public static RpcRows decodeRows(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            return readRows(buf);
        } finally {
            buf.release();
        }
    }

    public static long[] decodeMutation(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            return readMutation(buf);
        } finally {
            buf.release();
        }
    }

    public static byte[] encodeBatch(String sql, Object[][] paramRows) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            writeBatch(buf, sql, paramRows);
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    public static RpcBatchPayload decodeBatch(byte[] data) {
        PacketBuffer buf = new PacketBuffer(data);
        try {
            return readBatch(buf);
        } finally {
            buf.release();
        }
    }

    public static byte[] encodeBatchResult(int[] updateCounts) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
        try {
            writeBatchResult(buf, updateCounts);
            return toBytes(buf);
        } finally {
            buf.release();
        }
    }

    public static int[] decodeBatchResult(byte[] data) {
        PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer(data.length);
        buf.writeBytes(data);
        try {
            return readBatchResult(buf);
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
    public record RpcBatchPayload(String sql, Object[][] paramRows) {}
    public record RpcRows(String[] columns, Object[][] rows) {}
}
