package dev.sweety.versioning.version;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;

import java.util.UUID;

public record LauncherInfo(UUID clientId, Version launcher, Version app) implements Encoder {

    public static final CallableDecoder<LauncherInfo> DECODER =
            buffer -> new LauncherInfo(buffer.readUuid(), buffer.readObject(Version.DECODER), buffer.readObject(Version.DECODER));

    @Override
    public void write(final PacketBuffer buffer) {
        buffer.writeUuid(this.clientId).writeObject(this.launcher).writeObject(this.app);
    }
}
