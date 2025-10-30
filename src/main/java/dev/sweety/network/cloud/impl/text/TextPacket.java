package dev.sweety.network.cloud.impl.text;

import dev.sweety.network.cloud.impl.PacketRegistry;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.model.IPacket;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import lombok.Getter;

public interface TextPacket extends IPacket {
    @Getter
    class In extends PacketIn implements TextPacket{
        private final String text;

        public In(PacketIn packet) {
            super(packet);
            this.text = this.buffer.readString();
        }
    }

    class Out extends PacketOut implements TextPacket {
        public Out(String text) {
            super(PacketRegistry.TEXT.id());
            this.buffer.writeString(text);
        }
    }
}
