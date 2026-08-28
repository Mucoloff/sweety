package dev.sweety.versioning.security;

import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HandshakeProofTest {

    private static final UUID BUILD = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void emptySecret_yieldsZeroTag() {
        Map<Artifact, Version> versions = new LinkedHashMap<>();
        versions.put(Artifact.APP, new Version(1, 0, 0));
        byte[] tag = HandshakeProof.compute("", BUILD, CLIENT, versions, Channel.STABLE);
        assertEquals(HandshakeProof.LENGTH, tag.length);
        assertArrayEquals(new byte[HandshakeProof.LENGTH], tag);

        byte[] tagNull = HandshakeProof.compute(null, BUILD, CLIENT, versions, Channel.STABLE);
        assertArrayEquals(new byte[HandshakeProof.LENGTH], tagNull);
    }

    @Test
    void sameInputs_sameTag() {
        Map<Artifact, Version> a = new LinkedHashMap<>();
        a.put(Artifact.APP, new Version(1, 2, 3));
        a.put(Artifact.LAUNCHER, new Version(0, 0, 1));

        Map<Artifact, Version> b = new LinkedHashMap<>();
        b.put(Artifact.LAUNCHER, new Version(0, 0, 1));
        b.put(Artifact.APP, new Version(1, 2, 3));

        byte[] t1 = HandshakeProof.compute("shared-secret", BUILD, CLIENT, a, Channel.BETA);
        byte[] t2 = HandshakeProof.compute("shared-secret", BUILD, CLIENT, b, Channel.BETA);

        assertArrayEquals(t1, t2);
        assertFalse(java.util.Arrays.equals(t1, new byte[HandshakeProof.LENGTH]));
    }

    @Test
    void differentSecret_differentTag() {
        Map<Artifact, Version> versions = Map.of(
                Artifact.APP, new Version(1, 0, 0));

        byte[] t1 = HandshakeProof.compute("secret-a", BUILD, CLIENT, versions, Channel.STABLE);
        byte[] t2 = HandshakeProof.compute("secret-b", BUILD, CLIENT, versions, Channel.STABLE);

        assertFalse(java.util.Arrays.equals(t1, t2));
    }
}
