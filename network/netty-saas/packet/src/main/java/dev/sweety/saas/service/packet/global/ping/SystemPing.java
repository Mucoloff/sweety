package dev.sweety.saas.service.packet.global.ping;

import dev.sweety.netty.packet.model.Packet;

public class SystemPing extends Packet {

    public SystemPing() {

    }

    public SystemPing(long timestamp) {
        super(timestamp);
    }

    public SystemPing(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
    }
}
