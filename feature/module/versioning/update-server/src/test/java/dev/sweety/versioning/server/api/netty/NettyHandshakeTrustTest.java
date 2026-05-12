package dev.sweety.versioning.server.api.netty;

import dev.sweety.versioning.security.HandshakeProof;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NettyHandshakeTrustTest {

    private String prevSecret;

    private static final UUID BUILD = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final Map<Artifact, Version> VERSIONS = Map.of(Artifact.APP, new Version(1, 0, 0));

    @BeforeEach
    void saveSecret() {
        prevSecret = Settings.NETTY_HANDSHAKE_SECRET;
    }

    @AfterEach
    void restoreSecret() {
        Settings.NETTY_HANDSHAKE_SECRET = prevSecret != null ? prevSecret : "";
    }

    @Test
    void emptySecret_acceptsMatchingStructuralAndIgnoresProof() {
        Settings.NETTY_HANDSHAKE_SECRET = "";
        byte[] wrong = HandshakeProof.compute("other", BUILD, CLIENT, VERSIONS, Channel.STABLE);
        LauncherInfo info = new LauncherInfo(BUILD, CLIENT, VERSIONS, Channel.STABLE, wrong);
        assertTrue(NettyHandshakeTrust.isAcceptable(info));
    }

    @Test
    void nonEmptySecret_rejectsWrongProof() {
        Settings.NETTY_HANDSHAKE_SECRET = "server-secret";
        byte[] wrong = HandshakeProof.compute("client-wrong", BUILD, CLIENT, VERSIONS, Channel.STABLE);
        LauncherInfo info = new LauncherInfo(BUILD, CLIENT, VERSIONS, Channel.STABLE, wrong);
        assertFalse(NettyHandshakeTrust.isAcceptable(info));
    }

    @Test
    void nonEmptySecret_acceptsExpectedProof() {
        Settings.NETTY_HANDSHAKE_SECRET = "shared";
        byte[] proof = HandshakeProof.compute("shared", BUILD, CLIENT, VERSIONS, Channel.STABLE);
        LauncherInfo info = new LauncherInfo(BUILD, CLIENT, VERSIONS, Channel.STABLE, proof);
        assertTrue(NettyHandshakeTrust.isAcceptable(info));
    }
}
