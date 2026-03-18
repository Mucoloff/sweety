package dev.sweety.versioning.version;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.util.EnumMap;
import java.util.UUID;

public record LauncherInfo(UUID buildId, UUID clientId, EnumMap<Artifact, Version> versions, Channel channel) implements Encoder {

    public static final CallableDecoder<LauncherInfo> DECODER =
            buffer -> new LauncherInfo(
                    buffer.readUuid(),
                    buffer.readUuid(),
                    buffer.readEnumMap(Artifact.class, Version.DECODER),
                    buffer.readEnum(Channel.class)
            );

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeUuid(this.buildId).writeUuid(this.clientId).writeEnumMap(versions, PacketBuffer::writeObject).writeEnum(this.channel);
    }
}
