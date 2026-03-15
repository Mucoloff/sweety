package dev.sweety.versioning.protocol.update;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.versioning.version.ReleaseInfo;

public class ReleasePacket extends Packet {

    private ReleaseInfo state;
    public boolean forced;

    public ReleasePacket(ReleaseInfo state, boolean forced) {
        this.buffer().writeObject(state);
        this.buffer().writeBoolean(forced);
    }

    public ReleasePacket(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.state = this.buffer().readObject(ReleaseInfo.DECODER);
        this.forced = this.buffer().readBoolean();
    }

    public ReleaseInfo state() {
        return state;
    }

    public boolean forced() {
        return forced;
    }
}
