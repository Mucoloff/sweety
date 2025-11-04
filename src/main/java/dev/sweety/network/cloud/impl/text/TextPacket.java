package dev.sweety.network.cloud.impl.text;

import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;

public class TextPacket extends Packet {

    @Getter
    private String text;

    public TextPacket(String text) {
        this.buffer.writeString(text);
    }

    public TextPacket(byte id, long timestamp, byte[] data) {
        super(id, timestamp, data);
        this.text = this.buffer.readString();
    }
}

