package dev.sweety.versioning.security;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.PacketBufferAllocator;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * HMAC-SHA256 proof over the handshake identity payload. Canonical encoding matches
 * {@link dev.sweety.versioning.version.LauncherInfo} fields (excluding the proof), with map entries
 * ordered by artifact name for a stable MAC input.
 */
public final class HandshakeProof {

    public static final int LENGTH = 32;

    private static final String HMAC_ALG = "HmacSHA256";

    private HandshakeProof() {}

    public static byte[] compute(
            String secret,
            UUID buildId,
            UUID clientId,
            Map<Artifact, Version> versions,
            Channel channel) {
        if (secret == null || secret.isEmpty()) {
            return new byte[LENGTH];
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            PacketBuffer buf = PacketBufferAllocator.DEFAULT.buffer();
            try {
                buf.writeUuid(buildId);
                buf.writeUuid(clientId);
                versions.entrySet().stream()
                        .sorted(Comparator.comparing(e -> e.getKey().name()))
                        .forEach(e -> {
                            buf.writeString(e.getKey().name());
                            buf.writeObject(e.getValue());
                        });
                buf.writeEnum(channel);
                byte[] tag = mac.doFinal(buf.readAllBytes());
                if (tag.length != LENGTH) {
                    throw new IllegalStateException("unexpected HMAC length: " + tag.length);
                }
                return tag;
            } finally {
                buf.release();
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
