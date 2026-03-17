package dev.sweety.versioning.protocol.handshake;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;
import dev.sweety.versioning.version.Version;

public record ResponseData(String token, Version version) implements Encoder {

    public static final CallableDecoder<ResponseData> DECODER = buffer -> new ResponseData(buffer.readString(), buffer.readObject(Version.DECODER));

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeString(this.token).writeObject(this.version);
    }
}
