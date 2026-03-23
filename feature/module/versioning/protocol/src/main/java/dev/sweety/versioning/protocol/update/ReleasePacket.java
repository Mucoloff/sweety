package dev.sweety.versioning.protocol.update;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;

public class ReleasePacket extends Packet {

    private Artifact artifact;
    private ReleaseInfo info;
    private ReleaseBroadcastType type;

    public ReleasePacket(Artifact artifact, ReleaseInfo info, ReleaseBroadcastType type) {
        this.buffer().writeEnum(artifact);
        this.buffer().writeObject(info);
        this.buffer().writeEnum(type);
    }

    public ReleasePacket(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.artifact = this.buffer().readEnum(Artifact.class);
        this.info = this.buffer().readObject(ReleaseInfo.DECODER);
        this.type = this.buffer().readEnum(ReleaseBroadcastType.class);
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
