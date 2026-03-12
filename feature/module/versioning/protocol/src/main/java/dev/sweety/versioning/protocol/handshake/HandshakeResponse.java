package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.versioning.version.Version;
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
    private Optional<String> appToken;
    private Optional<Version> appVersion;
    private Optional<String> launcherToken;
    private Optional<Version> launcherVersion;


    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeEnum(this.state)
                .writeOptional(this.appToken, PacketBuffer::writeString)
                .writeOptional(this.appVersion, PacketBuffer::writeObject)
                .writeOptional(this.launcherToken, PacketBuffer::writeString)
                .writeOptional(this.launcherVersion, PacketBuffer::writeObject);
    }

    @Override
    public void read(final PacketBuffer buffer) {
        this.state = buffer.readEnum(State.class);
        this.appToken = buffer.readOptional(PacketBuffer::readString);
        this.appVersion = buffer.readOptional(buf -> buf.readObject(Version.DECODER));
        this.launcherToken = buffer.readOptional(PacketBuffer::readString);
        this.launcherVersion = buffer.readOptional(buf -> buf.readObject(Version.DECODER));
    }

    private static HandshakeResponse empty(State state) {
        return new HandshakeResponse(state, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static HandshakeResponse upToDate() {
        return empty(State.UP_TO_DATE);
    }

    public static HandshakeResponse unavailable() {
        return empty(State.UNAVAILABLE);
    }

    public static HandshakeResponse app(@NotNull String appToken, @NotNull Version appVersion) {
        return new HandshakeResponse(State.APP, Optional.of(appToken), Optional.of(appVersion), Optional.empty(), Optional.empty());
    }

    public static HandshakeResponse launcher(@NotNull String launcherToken, @NotNull Version launcherVersion) {
        return new HandshakeResponse(State.LAUNCHER, Optional.empty(), Optional.empty(), Optional.of(launcherToken), Optional.of(launcherVersion));
    }

    public static HandshakeResponse both(@NotNull String appToken, @NotNull Version appVersion, @NotNull String launcherToken, @NotNull Version launcherVersion) {
        return new HandshakeResponse(State.BOTH, Optional.of(appToken), Optional.of(appVersion), Optional.of(launcherToken), Optional.of(launcherVersion));
    }

}
