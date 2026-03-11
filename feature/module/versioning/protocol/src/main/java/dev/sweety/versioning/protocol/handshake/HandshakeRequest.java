package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.versioning.version.LauncherInfo;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HandshakeRequest extends PacketTransaction.Transaction {

    private LauncherInfo info;

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeObject(info);
    }

    @Override
    public void read(final PacketBuffer buffer) {
        this.info = buffer.readObject(LauncherInfo.DECODER);
    }

}
