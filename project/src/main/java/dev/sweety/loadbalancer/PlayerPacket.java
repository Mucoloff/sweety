package dev.sweety.loadbalancer;

import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

import java.util.UUID;

public class PlayerPacket extends Packet {

    @Getter
    private final UUID uuid;

    @Getter
    private final String text;

    public PlayerPacket(UUID uuid, String text) {
        this.buffer().writeUuid(this.uuid = uuid);
        this.buffer().writeString(this.text = text);
    }

    public PlayerPacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.uuid = this.buffer().readUuid();
        this.text = this.buffer().readString();
    }
}
