package dev.sweety.versioning.server.token;

import dev.sweety.time.Expirable;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.CRC32;

public record Token(UUID clientId, Artifact type, Version version, Channel channel, long expireAt,
                    UUID token) implements Expirable {

    private static final byte[] SECRET = Settings.tokenGeneratorSalt.getBytes(StandardCharsets.UTF_8); //todo SECRET!

    public Token(UUID clientId, Artifact type, Version version, Channel channel, long delay) {
        this(clientId, type, version, channel, System.currentTimeMillis() + delay, token(clientId, type, version, delay));
    }

    private static UUID token(UUID clientId, Artifact type, Version version, long expireAt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");

            ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 8 + 8);

            buffer.putLong(clientId.getMostSignificantBits());
            buffer.putLong(clientId.getLeastSignificantBits());
            buffer.putInt(type.ordinal());
            buffer.putLong(version.hashCode());
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