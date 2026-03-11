package dev.sweety.versioning.protocol.update;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.versioning.version.LatestInfo;

public class ReleasePacket extends Packet {

    private LatestInfo state;

    public ReleasePacket(LatestInfo state) {
        this.buffer().writeObject(state);
    }

    public ReleasePacket(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.state = this.buffer().readObject(LatestInfo.DECODER);
    }

    public LatestInfo state() {
        return state;
    }
}
