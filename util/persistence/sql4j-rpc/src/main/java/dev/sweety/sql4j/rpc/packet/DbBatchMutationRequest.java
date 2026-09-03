package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.rpc.RpcCodec;

/**
 * RPC request carrying a batch mutation payload with zero-copy stream serialization.
 */
public final class DbBatchMutationRequest extends Packet {

    private String sql;
    private Object[][] paramRows;
    private byte[] cachedPayload;

    public DbBatchMutationRequest() {}

    public DbBatchMutationRequest(String sql, Object[][] paramRows) {
        this.sql = sql;
        this.paramRows = paramRows;
    }

    public DbBatchMutationRequest(byte[] payload) {
        this.cachedPayload = payload;
        if (payload != null && payload.length > 0) {
            RpcCodec.RpcBatchPayload b = RpcCodec.decodeBatch(payload);
            this.sql = b.sql();
            this.paramRows = b.paramRows();
        }
    }

    public String sql() { return sql; }
    public Object[][] paramRows() { return paramRows; }

    public byte[] payload() {
        if (cachedPayload == null) {
            cachedPayload = RpcCodec.encodeBatch(sql, paramRows);
        }
        return cachedPayload;
    }

    @Override public void write(BufferWriter buffer) {
        RpcCodec.writeBatch(buffer, sql, paramRows);
    }

    @Override public void read(BufferReader buffer) {
        RpcCodec.RpcBatchPayload b = RpcCodec.readBatch(buffer);
        this.sql = b.sql();
        this.paramRows = b.paramRows();
        this.cachedPayload = null;
    }
}
