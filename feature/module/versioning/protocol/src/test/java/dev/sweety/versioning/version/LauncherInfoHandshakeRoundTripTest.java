package dev.sweety.versioning.version;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.versioning.security.HandshakeProof;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test: handshake payload including HMAC proof survives codec round-trip
 * (deploy launcher and server must ship the same protocol revision).
 */
class LauncherInfoHandshakeRoundTripTest {

    private static final UUID BUILD = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();

    @Test
    void roundTrip_withNonZeroProof_preservesBytes() {
        Map<Artifact, Version> versions = Map.of(
                Artifact.APP, new Version(1, 2, 3));
        byte[] proof = HandshakeProof.compute("rollout-secret", BUILD, CLIENT, versions, Channel.BETA);

        LauncherInfo original = new LauncherInfo(BUILD, CLIENT, versions, Channel.BETA, proof);

        PacketBuffer buf = new PacketBuffer();
        original.write(buf);
        LauncherInfo decoded = LauncherInfo.DECODER.read(buf);

        assertEquals(original, decoded);
        assertArrayEquals(proof, decoded.handshakeProof());
    }
}
