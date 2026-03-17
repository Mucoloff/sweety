package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HandshakeResponse extends PacketTransaction.Transaction {

    private State state;
    private EnumMap<Artifact, ResponseData> versions;

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

}
