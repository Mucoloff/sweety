package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.rpc.RpcCodec;

/**
 * RPC response for a {@link DbBatchMutationRequest}: either a zero-copy per-row update-count
 * array or an {@link #error}.
 */
public final class DbBatchMutationResponse extends Packet {

    private int[] updateCounts;
    private byte[] cachedPayload;
    private String error;

    public DbBatchMutationResponse() {}

    public DbBatchMutationResponse(int[] updateCounts) {
        this.updateCounts = updateCounts != null ? updateCounts : new int[0];
        this.error = null;
    }

    public DbBatchMutationResponse(byte[] payload) {
        this.cachedPayload = payload != null ? payload : new byte[0];
        if (payload != null && payload.length > 0) {
            this.updateCounts = RpcCodec.decodeBatchResult(payload);
        } else {
            this.updateCounts = new int[0];
        }
        this.error = null;
    }

    public static DbBatchMutationResponse error(String error) {
        DbBatchMutationResponse r = new DbBatchMutationResponse();
        r.error = error != null ? error : "unknown error";
        r.updateCounts = new int[0];
        return r;
    }

    public int[] updateCounts() { return updateCounts; }
    public String error() { return error; }

    public byte[] payload() {
        if (cachedPayload == null && error == null) {
            cachedPayload = RpcCodec.encodeBatchResult(updateCounts);
        }
        return cachedPayload != null ? cachedPayload : new byte[0];
    }

    @Override public void write(BufferWriter buffer) {
        boolean failed = error != null;
        buffer.writeBoolean(failed);
        if (failed) {
            buffer.writeString(error);
        } else {
            RpcCodec.writeBatchResult(buffer, updateCounts);
        }
    }

    @Override public void read(BufferReader buffer) {
        boolean failed = buffer.readBoolean();
        if (failed) {
            error = buffer.readString();
            updateCounts = new int[0];
            cachedPayload = new byte[0];
        } else {
            error = null;
            updateCounts = RpcCodec.readBatchResult(buffer);
            cachedPayload = null;
        }
    }
}
