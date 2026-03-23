package dev.sweety.versioning.version;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public record ReleaseInfo(
        Version version,
        Channel channel,
        Instant updatedAt,
        float rollout
) implements Encoder {

    public ReleaseInfo(Version version, Channel channel, float rollout) {
        this(version, channel, Instant.now(), rollout);
    }

    public static ReleaseInfo DEFAULT(Channel channel) {
        return new ReleaseInfo(Version.ZERO, channel, Instant.MIN, 0f);
    }

    public static final CallableDecoder<ReleaseInfo> DECODER = buffer -> new ReleaseInfo(
            buffer.readObject(Version.DECODER),
            buffer.readEnum(Channel.class),
            Instant.ofEpochMilli(buffer.readVarLong()),
            buffer.readFloat());

    public static ReleaseInfo of(Version version, Channel channel, @Nullable Float rollout) {
        return new ReleaseInfo(version, channel, rollout != null ? rollout : 1f);
    }

    @Override
    public float rollout() {
        return Math.max(0f, Math.min(1f, this.rollout));
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeObject(this.version).writeEnum(this.channel).writeVarLong(updatedAt.toEpochMilli()).writeFloat(rollout);
    }
}