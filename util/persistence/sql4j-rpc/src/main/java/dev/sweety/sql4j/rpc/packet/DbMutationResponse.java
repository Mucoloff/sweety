package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.rpc.RpcCodec;

/**
 * RPC response for a {@link DbMutationRequest}: either a zero-copy {updateCount, generatedKey}
 * result or an {@link #error} string.
 */
public final class DbMutationResponse extends Packet {

    private int updateCount;
    private long generatedKey;
    private byte[] cachedPayload;
    private String error;

    public DbMutationResponse() {}

    public DbMutationResponse(int updateCount, long generatedKey) {
        this.updateCount = updateCount;
        this.generatedKey = generatedKey;
        this.error = null;
    }

    public DbMutationResponse(byte[] payload) {
        this.cachedPayload = payload != null ? payload : new byte[0];
        if (payload != null && payload.length > 0) {
            long[] res = RpcCodec.decodeMutation(payload);
            this.updateCount = (int) res[0];
            this.generatedKey = res[1];
        }
        this.error = null;
    }

    public static DbMutationResponse error(String error) {
        DbMutationResponse r = new DbMutationResponse();
        r.error = error != null ? error : "unknown error";
        return r;
    }

    public int updateCount() { return updateCount; }
    public long generatedKey() { return generatedKey; }
    public String error() { return error; }

    public byte[] payload() {
        if (cachedPayload == null && error == null) {
            cachedPayload = RpcCodec.encodeMutation(updateCount, generatedKey);
        }
        return cachedPayload != null ? cachedPayload : new byte[0];
    }

    @Override public void write(BufferWriter buffer) {
        boolean failed = error != null;
        buffer.writeBoolean(failed);
        if (failed) {
            buffer.writeString(error);
        } else {
            RpcCodec.writeMutation(buffer, updateCount, generatedKey);
        }
    }

    @Override public void read(BufferReader buffer) {
        boolean failed = buffer.readBoolean();
        if (failed) {
            error = buffer.readString();
            updateCount = 0;
            generatedKey = 0L;
            cachedPayload = new byte[0];
        } else {
            error = null;
            long[] res = RpcCodec.readMutation(buffer);
            this.updateCount = (int) res[0];
            this.generatedKey = res[1];
            this.cachedPayload = null;
        }
    }
}
