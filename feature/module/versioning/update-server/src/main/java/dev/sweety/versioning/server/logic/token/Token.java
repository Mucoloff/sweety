package dev.sweety.versioning.server.logic.token;

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
import java.util.zip.CRC32;

public record Token(UUID clientId, Artifact artifact, Channel channel, Version version, Version from, DownloadType downloadType,
                    long expireAt,
                    UUID token) implements Expirable {

    private static final byte[] SECRET = Settings.TOKEN_GEN_SALT.getBytes(StandardCharsets.UTF_8);

    public Token(UUID clientId, Artifact artifact, Channel channel, Version version, Version from, DownloadType downloadType, long delay) {
        this(clientId, artifact, channel, version, from, downloadType, System.currentTimeMillis() + delay, token(clientId, artifact, version, channel, downloadType, delay));
    }

    private static UUID token(UUID clientId, Artifact artifact, Version version, Channel channel, DownloadType downloadType, long expireAt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");

            ByteBuffer buffer = ByteBuffer.allocate(8 + 8 + 4 + 8 + 4 + 4 + 8);

            buffer.putLong(clientId.getMostSignificantBits());
            buffer.putLong(clientId.getLeastSignificantBits());
            buffer.putInt(artifact.ordinal());
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

    /*@Override
    public long now() {
        return System.nanoTime();
    }*/
}