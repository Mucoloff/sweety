package dev.sweety.sql4j.rpc.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

/** RPC request carrying an {@link dev.sweety.sql4j.rpc.RpcCodec}-encoded SELECT query payload. */
public final class DbQueryRequest extends Packet {

    private byte[] payload = new byte[0];

    public DbQueryRequest() {}

    public DbQueryRequest(byte[] payload) {
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
