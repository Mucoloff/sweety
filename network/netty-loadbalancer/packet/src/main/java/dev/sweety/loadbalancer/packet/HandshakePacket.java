package dev.sweety.loadbalancer.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

public final class HandshakePacket extends Packet {

    private String clientVersion;
    private String authKey;
    private long requestedSessionId;

    public HandshakePacket() {}

    public HandshakePacket(String clientVersion, String authKey, long requestedSessionId) {
        this.clientVersion = clientVersion;
        this.authKey = authKey;
        this.requestedSessionId = requestedSessionId;
    }

    public static HandshakePacket of(String clientVersion, String authKey, long requestedSessionId) {
        return new HandshakePacket(clientVersion, authKey, requestedSessionId);
    }

    public String clientVersion() { return clientVersion; }
    public String authKey() { return authKey; }
    public long requestedSessionId() { return requestedSessionId; }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeString(clientVersion);
        buffer.writeString(authKey);
        buffer.writeLong(requestedSessionId);
    }

    @Override
    public void read(BufferReader buffer) {
        this.clientVersion = buffer.readString();
        this.authKey = buffer.readString();
        this.requestedSessionId = buffer.readLong();
    }
}
