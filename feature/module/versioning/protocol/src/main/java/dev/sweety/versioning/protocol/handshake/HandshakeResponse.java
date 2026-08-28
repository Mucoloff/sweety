package dev.sweety.versioning.protocol.handshake;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.versioning.version.artifact.Artifact;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HandshakeResponse extends PacketTransaction.Transaction {

    private State state;
    private Map<Artifact, ResponseData> versions;

    public HandshakeResponse() {
    }

    public HandshakeResponse(final State state, final Map<Artifact, ResponseData> versions) {
        this.state = state;
        this.versions = versions;
    }

    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }

    public Map<Artifact, ResponseData> getVersions() {
        return versions;
    }

    public void setVersions(final Map<Artifact, ResponseData> versions) {
        this.versions = versions;
    }

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeEnum(this.state);
        buffer.writeMap(versions, (b, artifact) -> b.writeString(artifact.name()), (b, data) -> b.writeObject(data));
    }

    @Override
    public void read(final BufferReader buffer) {
        this.state = buffer.readEnum(State.class);
        this.versions = buffer.readMap(
                b -> new Artifact(b.readString()),
                b -> b.readObject(ResponseData.DECODER),
                HashMap::new
        );
    }

    private static HandshakeResponse empty(State state) {
        return new HandshakeResponse(state, new HashMap<>());
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