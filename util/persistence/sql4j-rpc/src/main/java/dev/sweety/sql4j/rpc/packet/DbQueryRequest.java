package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.rpc.RpcCodec;

/** RPC request carrying a SELECT query payload with zero-copy stream serialization. */
public final class DbQueryRequest extends Packet {

    private String sql;
    private Object[] params;
    private boolean returnGeneratedKeys;
    private byte[] cachedPayload;

    public DbQueryRequest() {}

    public DbQueryRequest(String sql, Object[] params, boolean returnGeneratedKeys) {
        this.sql = sql;
        this.params = params;
        this.returnGeneratedKeys = returnGeneratedKeys;
    }

    public DbQueryRequest(byte[] payload) {
        this.cachedPayload = payload;
        if (payload != null && payload.length > 0) {
            RpcCodec.RpcQueryPayload q = RpcCodec.decodeQuery(payload);
            this.sql = q.sql();
            this.params = q.params();
            this.returnGeneratedKeys = q.returnGeneratedKeys();
        }
    }

    public String sql() { return sql; }
    public Object[] params() { return params; }
    public boolean returnGeneratedKeys() { return returnGeneratedKeys; }

    public byte[] payload() {
        if (cachedPayload == null) {
            cachedPayload = RpcCodec.encodeQuery(sql, params, returnGeneratedKeys);
        }
        return cachedPayload;
    }

    @Override public void write(BufferWriter buffer) {
        RpcCodec.writeQuery(buffer, sql, params, returnGeneratedKeys);
    }

    @Override public void read(BufferReader buffer) {
        RpcCodec.RpcQueryPayload q = RpcCodec.readQuery(buffer);
        this.sql = q.sql();
        this.params = q.params();
        this.returnGeneratedKeys = q.returnGeneratedKeys();
        this.cachedPayload = null;
    }
}
