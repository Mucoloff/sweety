package dev.sweety.versioning.version;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.versioning.version.channel.Channel;

import java.time.Instant;

public record ReleaseInfo(Version version, Channel channel, Instant updatedAt) implements Encoder {

    public ReleaseInfo(Version version, Channel channel) {
        this(version, channel, Instant.now());
    }

    public static final ReleaseInfo DEFAULT = new ReleaseInfo(Version.ZERO, Channel.STABLE, Instant.MIN);

    public static final CallableDecoder<ReleaseInfo> DECODER = buffer -> new ReleaseInfo(
            buffer.readObject(Version.DECODER),
            buffer.readEnum(Channel.class),
            Instant.ofEpochMilli(buffer.readVarLong()));

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeObject(this.version).writeEnum(this.channel).writeVarLong(updatedAt.toEpochMilli());
    }
}