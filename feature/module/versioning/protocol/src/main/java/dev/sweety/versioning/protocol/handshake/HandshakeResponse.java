package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HandshakeResponse extends PacketTransaction.Transaction {

    private State state;
    //todo add versions
    private Optional<String> appToken, launcherToken;

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeEnum(this.state)
                .writeOptional(this.appToken, PacketBuffer::writeString)
                .writeOptional(this.launcherToken, PacketBuffer::writeString);
    }

    @Override
    public void read(final PacketBuffer buffer) {
        this.state = buffer.readEnum(State.class);
        this.appToken = buffer.readOptional(PacketBuffer::readString);
        this.launcherToken = buffer.readOptional(PacketBuffer::readString);
    }

    public static HandshakeResponse upToDate() {
        return new HandshakeResponse(State.UP_TO_DATE, Optional.empty(), Optional.empty());
    }

    public static HandshakeResponse unavailable() {
        return new HandshakeResponse(State.UNAVAILABLE, Optional.empty(), Optional.empty());
    }

    public static HandshakeResponse app(@NotNull String appToken) {
        return new HandshakeResponse(State.APP, Optional.of(appToken), Optional.empty());
    }

    public static HandshakeResponse launcher(@NotNull String  launcherToken) {
        return new HandshakeResponse(State.LAUNCHER, Optional.empty(), Optional.of(launcherToken));
    }

    public static HandshakeResponse both(@NotNull String appToken, @NotNull String launcherToken){
        return new HandshakeResponse(State.BOTH, Optional.of(appToken), Optional.of(launcherToken));
    }

}
