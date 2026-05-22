package dev.sweety.versioning.server.adapter.in.netty;

import dev.sweety.versioning.security.HandshakeProof;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.version.LauncherInfo;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * Basic validation for {@link LauncherInfo} on the Netty handshake path.
 * Rejects nil UUIDs and empty version maps so anonymous or trivial clients cannot drive update decisions.
 * When {@link Settings#NETTY_HANDSHAKE_SECRET} is non-blank, requires a matching HMAC proof.
 */
public final class NettyHandshakeTrust {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private NettyHandshakeTrust() {}

    public static boolean isAcceptable(LauncherInfo info) {
        if (info == null || info.channel() == null) {
            return false;
        }
        if (info.clientId() == null || NIL_UUID.equals(info.clientId())) {
            return false;
        }
        if (info.buildId() == null || NIL_UUID.equals(info.buildId())) {
            return false;
        }
        if (info.versions() == null || info.versions().isEmpty()
                || info.versions().keySet().stream().anyMatch(a -> a == null || a.name() == null || a.name().isBlank())) {
            return false;
        }
        String secret = Settings.NETTY_HANDSHAKE_SECRET;
        if (secret == null || secret.isEmpty()) {
            return true;
        }
        byte[] expected = HandshakeProof.compute(secret, info.buildId(), info.clientId(), info.versions(), info.channel());
        return MessageDigest.isEqual(expected, info.handshakeProof());
    }
}
