package dev.sweety.versioning.version;

import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record LauncherInfo(UUID buildId, UUID clientId, Map<Artifact, Version> versions, Channel channel) implements Encoder {

    public static final CallableDecoder<LauncherInfo> DECODER =
            buffer -> new LauncherInfo(
                    buffer.readUuid(),
                    buffer.readUuid(),
                    buffer.readMap(
                            b -> new Artifact(b.readString()),
                            b -> b.readObject(Version.DECODER),
                            HashMap::new
                    ),
                    buffer.readEnum(Channel.class)
            );

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeUuid(this.buildId).writeUuid(this.clientId);
        buffer.writeMap(versions, (b, artifact) -> b.writeString(artifact.name()), (b, version) -> b.writeObject(version));
        buffer.writeEnum(this.channel);
    }
}
