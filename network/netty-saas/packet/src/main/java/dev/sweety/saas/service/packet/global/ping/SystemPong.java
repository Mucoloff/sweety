package dev.sweety.saas.service.packet.global.ping;

import dev.sweety.netty.packet.model.Packet;

public class SystemPong extends Packet {

    public SystemPong(long timestamp) {
        super(timestamp);
    }

    public SystemPong(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
    }
}
