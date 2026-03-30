package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.versioning.version.artifact.Artifact;

import java.util.EnumMap;
import java.util.Objects;

public class HandshakeResponse extends PacketTransaction.Transaction {

    private State state;
    private EnumMap<Artifact, ResponseData> versions;

    public HandshakeResponse() {
    }

    public HandshakeResponse(final State state, final EnumMap<Artifact, ResponseData> versions) {
        this.state = state;
        this.versions = versions;
    }

    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }

    public EnumMap<Artifact, ResponseData> getVersions() {
        return versions;
    }

    public void setVersions(final EnumMap<Artifact, ResponseData> versions) {
        this.versions = versions;
    }

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeEnum(this.state).writeEnumMap(versions, PacketBuffer::writeObject);
    }

    @Override
    public void read(final PacketBuffer buffer) {
        this.state = buffer.readEnum(State.class);
        this.versions = buffer.readEnumMap(Artifact.class, ResponseData.DECODER);
    }

    private static HandshakeResponse empty(State state) {
        return new HandshakeResponse(state, new EnumMap<>(Artifact.class));
    }

    public static HandshakeResponse upToDate() {
        return empty(State.UP_TO_DATE);
    }

    public static HandshakeResponse unavailable() {
        return empty(State.UNAVAILABLE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof HandshakeResponse that)) return false;
        if (!super.equals(o)) return false;
        return state == that.state && Objects.equals(versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), state, versions);
    }

    @Override
    public String toString() {
        return "HandshakeResponse{" +
                "state=" + state +
                ", versions=" + versions +
                '}';
    }
}