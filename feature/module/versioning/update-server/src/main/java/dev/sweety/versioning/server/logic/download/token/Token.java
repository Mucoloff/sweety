package dev.sweety.versioning.server.logic.download.token;

import dev.sweety.time.Expirable;
import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

public record Token(UUID clientId, Artifact artifact, Channel channel, Version version, Version from, DownloadType downloadType,
                    long expireAt,
                    UUID token) implements Expirable {

    private static final byte[] SECRET = Settings.TOKEN_GEN_SALT.getBytes(StandardCharsets.UTF_8);

    public Token(UUID clientId, Artifact artifact, Channel channel, Version version, Version from, DownloadType downloadType, long delayMs) {
        this(clientId, artifact, channel, version, from, downloadType, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs), token(clientId, artifact, version, channel, downloadType, delayMs));
    }

    private static UUID token(UUID clientId, Artifact artifact, Version version, Channel channel, DownloadType downloadType, long expireAt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");

            ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 4 + 8 + 4 + 4 + 8);

            buffer.putLong(clientId.getMostSignificantBits());
            buffer.putLong(clientId.getLeastSignificantBits());
            buffer.putInt(artifactKey(artifact));
            buffer.putLong(version.hashCode());
            buffer.putInt(channel.ordinal());
            buffer.putInt(downloadType.ordinal());
            buffer.putLong(expireAt);

            byte[] payload = buffer.array();

            CRC32 crc = new CRC32();
            crc.update(payload);

            sha.update(SECRET);
            sha.update(payload);
            sha.update(ByteBuffer.allocate(8).putLong(crc.getValue()).array());

            byte[] hash = sha.digest();

            ByteBuffer uuidBytes = ByteBuffer.wrap(hash);

            return new UUID(uuidBytes.getLong(), uuidBytes.getLong());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int artifactKey(Artifact artifact) {
        return switch (artifact.name()) {
            case "APP" -> 0;
            case "LAUNCHER" -> 1;
            default -> artifact.name().hashCode();
        };
    }

}