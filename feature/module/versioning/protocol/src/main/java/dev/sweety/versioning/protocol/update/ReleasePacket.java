package dev.sweety.versioning.protocol.update;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;

public class ReleasePacket extends Packet {

    private Artifact artifact;
    private ReleaseInfo info;
    private ReleaseBroadcastType type;

    public ReleasePacket() {}

    public ReleasePacket(Artifact artifact, ReleaseInfo info, ReleaseBroadcastType type) {
        this.artifact = artifact;
        this.info = info;
        this.type = type;
    }

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeString(artifact.name());
        buffer.writeObject(info);
        buffer.writeEnum(type);
    }

    @Override
    public void read(final BufferReader buffer) {
        this.artifact = new Artifact(buffer.readString());
        this.info = buffer.readObject(ReleaseInfo.DECODER);
        this.type = buffer.readEnum(ReleaseBroadcastType.class);
    }

    public Artifact artifact() {
        return artifact;
    }

    public ReleaseInfo info() {
        return info;
    }

    public ReleaseBroadcastType type() {
        return type;
    }
}
