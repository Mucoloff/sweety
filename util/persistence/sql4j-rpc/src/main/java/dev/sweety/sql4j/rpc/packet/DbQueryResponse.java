package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.rpc.RpcCodec;

/**
 * RPC response for a {@link DbQueryRequest}: either a zero-copy row matrix or an {@link #error} string.
 */
public final class DbQueryResponse extends Packet {

    private String[] columns;
    private Object[][] rows;
    private byte[] cachedPayload;
    private String error;

    public DbQueryResponse() {}

    public DbQueryResponse(String[] columns, Object[][] rows) {
        this.columns = columns != null ? columns : new String[0];
        this.rows = rows != null ? rows : new Object[0][];
        this.error = null;
    }

    public DbQueryResponse(byte[] payload) {
        this.cachedPayload = payload != null ? payload : new byte[0];
        if (payload != null && payload.length > 0) {
            RpcCodec.RpcRows r = RpcCodec.decodeRows(payload);
            this.columns = r.columns();
            this.rows = r.rows();
        } else {
            this.columns = new String[0];
            this.rows = new Object[0][];
        }
        this.error = null;
    }

    public static DbQueryResponse error(String error) {
        DbQueryResponse r = new DbQueryResponse();
        r.error = error != null ? error : "unknown error";
        r.columns = new String[0];
        r.rows = new Object[0][];
        return r;
    }

    public String[] columns() { return columns; }
    public Object[][] rows() { return rows; }
    public String error() { return error; }

    public byte[] payload() {
        if (cachedPayload == null && error == null) {
            cachedPayload = RpcCodec.encodeRows(columns, rows);
        }
        return cachedPayload != null ? cachedPayload : new byte[0];
    }

    @Override public void write(BufferWriter buffer) {
        boolean failed = error != null;
        buffer.writeBoolean(failed);
        if (failed) {
            buffer.writeString(error);
        } else {
            RpcCodec.writeRows(buffer, columns, rows);
        }
    }

    @Override public void read(BufferReader buffer) {
        boolean failed = buffer.readBoolean();
        if (failed) {
            error = buffer.readString();
            columns = new String[0];
            rows = new Object[0][];
            cachedPayload = new byte[0];
        } else {
            error = null;
            RpcCodec.RpcRows r = RpcCodec.readRows(buffer);
            columns = r.columns();
            rows = r.rows();
            cachedPayload = null;
        }
    }
}
