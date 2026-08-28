package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

/**
 * RPC response for a {@link DbQueryRequest}: either an encoded row matrix ({@link #payload})
 * or an {@link #error} string. Exactly one is meaningful; a non-null error marks failure.
 */
public final class DbQueryResponse extends Packet {

    private byte[] payload = new byte[0];
    private String error;

    public DbQueryResponse() {}

    public DbQueryResponse(byte[] payload) {
        this.payload = payload != null ? payload : new byte[0];
        this.error = null;
    }

    public static DbQueryResponse error(String error) {
        DbQueryResponse r = new DbQueryResponse();
        r.error = error != null ? error : "unknown error";
        return r;
    }

    public byte[] payload() { return payload; }
    public String error() { return error; }

    @Override public void write(BufferWriter buffer) {
        boolean failed = error != null;
        buffer.writeBoolean(failed);
        if (failed) {
            buffer.writeString(error);
        } else {
            buffer.writeByteArray(payload);
        }
    }

    @Override public void read(BufferReader buffer) {
        boolean failed = buffer.readBoolean();
        if (failed) {
            error = buffer.readString();
            payload = new byte[0];
        } else {
            error = null;
            payload = buffer.readByteArray();
        }
    }
}
