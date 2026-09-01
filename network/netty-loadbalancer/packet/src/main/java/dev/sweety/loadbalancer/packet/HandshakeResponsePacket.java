package dev.sweety.loadbalancer.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

public final class HandshakeResponsePacket extends Packet {

    private boolean accepted;
    private long assignedSessionId;
    private byte[] sessionToken;
    private String message;

    public HandshakeResponsePacket() {}

    public HandshakeResponsePacket(boolean accepted, long assignedSessionId, byte[] sessionToken, String message) {
        this.accepted = accepted;
        this.assignedSessionId = assignedSessionId;
        this.sessionToken = sessionToken;
        this.message = message;
    }

    public static HandshakeResponsePacket of(boolean accepted, long assignedSessionId, byte[] sessionToken, String message) {
        return new HandshakeResponsePacket(accepted, assignedSessionId, sessionToken, message);
    }

    public boolean accepted() { return accepted; }
    public long assignedSessionId() { return assignedSessionId; }
    public byte[] sessionToken() { return sessionToken; }
    public String message() { return message; }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeBoolean(accepted);
        buffer.writeLong(assignedSessionId);
        buffer.writeByteArray(sessionToken != null ? sessionToken : new byte[0]);
        buffer.writeString(message != null ? message : "");
    }

    @Override
    public void read(BufferReader buffer) {
        this.accepted = buffer.readBoolean();
        this.assignedSessionId = buffer.readLong();
        this.sessionToken = buffer.readByteArray();
        this.message = buffer.readString();
    }
}
