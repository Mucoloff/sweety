package dev.sweety.packet.text;

import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

@GenerateEvent
public class TextPacket extends Packet {

    @Getter
    private String text;

    public TextPacket(String text) {
        this.buffer().writeString(text);
    }

    public TextPacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.text = this.buffer().readString();
    }
}
