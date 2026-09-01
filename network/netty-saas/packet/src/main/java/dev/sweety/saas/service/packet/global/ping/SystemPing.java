package dev.sweety.saas.service.packet.global.ping;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;

public class SystemPing extends Packet {

    public SystemPing() {
    }

    public SystemPing(long timestamp) {
        super(timestamp);
    }

    @Override
    public void write(BufferWriter buffer) {
    }

    @Override
    public void read(BufferReader buffer) {
    }
}
