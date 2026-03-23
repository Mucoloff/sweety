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
        float rollout, Instant updatedAt
) implements Encoder {

    public ReleaseInfo(Version version, Channel channel, float rollout) {
        this(version, channel, rollout, Instant.now());
    }

    public static ReleaseInfo DEFAULT(Channel channel) {
        return new ReleaseInfo(Version.ZERO, channel, 0f, Instant.MIN);
    }

    public static final CallableDecoder<ReleaseInfo> DECODER = buffer -> new ReleaseInfo(
            buffer.readObject(Version.DECODER),
            buffer.readEnum(Channel.class),
            buffer.readFloat(), Instant.ofEpochMilli(buffer.readVarLong())
    );

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

    public ReleaseInfo withRollout(float rollout) {
        return new ReleaseInfo(version, channel, rollout);
    }
}