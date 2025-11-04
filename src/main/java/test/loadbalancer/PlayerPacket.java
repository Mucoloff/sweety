package test.loadbalancer;

import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;

import java.util.UUID;

public class PlayerPacket extends Packet {

    @Getter
    private final UUID uuid;

    @Getter
    private final String text;

    public PlayerPacket(UUID uuid, String text) {
        this.buffer.writeUuid(this.uuid = uuid);
        this.buffer.writeString(this.text = text);
    }

    public PlayerPacket(byte id, long timestamp, byte[] data) {
        super(id, timestamp, data);
        this.uuid = this.buffer.readUuid();
        this.text = this.buffer.readString();
    }
}
