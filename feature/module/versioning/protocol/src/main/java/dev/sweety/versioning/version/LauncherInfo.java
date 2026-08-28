package dev.sweety.versioning.version;

import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.versioning.security.HandshakeProof;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record LauncherInfo(UUID buildId, UUID clientId, Map<Artifact, Version> versions, Channel channel,
                           byte[] handshakeProof) implements Encoder {

    public LauncherInfo {
        Objects.requireNonNull(handshakeProof, "handshakeProof");
        if (handshakeProof.length != HandshakeProof.LENGTH) {
            throw new IllegalArgumentException("handshakeProof must be " + HandshakeProof.LENGTH + " bytes");
        }
        handshakeProof = handshakeProof.clone();
    }

    public static final CallableDecoder<LauncherInfo> DECODER =
            buffer -> {
                UUID buildId = buffer.readUuid();
                UUID clientId = buffer.readUuid();
                Map<Artifact, Version> versions = buffer.readMap(
                        Artifact.DECODER,
                        Version.DECODER,
                        HashMap::new
                );
                Channel channel = buffer.readEnum(Channel.class);
                byte[] proof = new byte[HandshakeProof.LENGTH];
                buffer.readBytes(proof);
                return new LauncherInfo(buildId, clientId, versions, channel, proof);
            };

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeUuid(this.buildId)
                .writeUuid(this.clientId)
                .writeMap(versions)
                .writeEnum(this.channel)
                .writeBytes(this.handshakeProof);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LauncherInfo(
                UUID build, UUID client, Map<Artifact, Version> vers, Channel chan, byte[] proof
        ))) return false;
        return buildId.equals(build) && clientId.equals(client) && versions.equals(vers) && channel == chan && Arrays.equals(handshakeProof, proof);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildId, clientId, versions, channel, Arrays.hashCode(handshakeProof));
    }
}
