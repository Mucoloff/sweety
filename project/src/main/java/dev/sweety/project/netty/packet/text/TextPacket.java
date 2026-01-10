package dev.sweety.project.netty.packet.text;

import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

@GenerateEvent
public class TextPacket extends Packet {

    @Getter
    private String text;

    public TextPacket(final String text) {
        this.buffer().writeString(text);
    }

    public TextPacket(final int _id,final long _timestamp,final byte[] _data) {
        super(_id, _timestamp, _data);
        this.text = this.buffer().readString();
    }
}
