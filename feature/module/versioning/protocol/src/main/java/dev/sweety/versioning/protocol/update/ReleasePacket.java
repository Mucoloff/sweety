package dev.sweety.versioning.protocol.update;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.versioning.version.LatestInfo;

public class ReleasePacket extends Packet {

    private LatestInfo state;
    public boolean forced;


    public ReleasePacket(LatestInfo state, boolean forced) {
        this.buffer().writeObject(state);
        this.buffer().writeBoolean(forced);
    }

    public ReleasePacket(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.state = this.buffer().readObject(LatestInfo.DECODER);
        this.forced = this.buffer().readBoolean();
    }

    public LatestInfo state() {
        return state;
    }

    public boolean forced() {
        return forced;
    }
}
