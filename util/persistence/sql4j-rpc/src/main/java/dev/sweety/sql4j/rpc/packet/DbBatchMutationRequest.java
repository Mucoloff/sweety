package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

/**
 * RPC request carrying an {@link dev.sweety.sql4j.rpc.RpcCodec}-encoded batch mutation payload
 * (one SQL text, N param rows) — the wire counterpart of a JDBC
 * {@code addBatch()}/{@code executeBatch()} sequence, sent as a single roundtrip instead of one
 * {@link DbMutationRequest} per row.
 */
public final class DbBatchMutationRequest extends Packet {

    private byte[] payload = new byte[0];

    public DbBatchMutationRequest() {}

    public DbBatchMutationRequest(byte[] payload) {
        this.payload = payload != null ? payload : new byte[0];
    }

    public byte[] payload() { return payload; }

    @Override public void write(BufferWriter buffer) {
        buffer.writeByteArray(payload);
    }

    @Override public void read(BufferReader buffer) {
        payload = buffer.readByteArray();
    }
}
