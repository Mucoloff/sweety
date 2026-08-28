package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

/**
 * RPC response for a {@link DbBatchMutationRequest}: either an encoded per-row update-count
 * array ({@link #payload}, per JDBC {@code executeBatch()} semantics) or an {@link #error}.
 */
public final class DbBatchMutationResponse extends Packet {

    private byte[] payload = new byte[0];
    private String error;

    public DbBatchMutationResponse() {}

    public DbBatchMutationResponse(byte[] payload) {
        this.payload = payload != null ? payload : new byte[0];
        this.error = null;
    }

    public static DbBatchMutationResponse error(String error) {
        DbBatchMutationResponse r = new DbBatchMutationResponse();
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
