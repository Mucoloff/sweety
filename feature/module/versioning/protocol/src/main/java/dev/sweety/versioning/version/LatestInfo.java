package dev.sweety.versioning.version;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.Encoder;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;

import java.time.Instant;

public record LatestInfo(Version launcher, Version app, Instant updatedAt) implements Encoder {

    public LatestInfo(Version launcher, Version app){
        this(launcher, app, Instant.now());
    }

    public static final LatestInfo DEFAULT = new LatestInfo(Version.ZERO, Version.ZERO, Instant.MIN);

    public static final CallableDecoder<LatestInfo> DECODER = buffer -> new LatestInfo(buffer.readObject(Version.DECODER), buffer.readObject(Version.DECODER), Instant.ofEpochMilli(buffer.readVarLong()));

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeObject(launcher).writeObject(app).writeVarLong(updatedAt.toEpochMilli());
    }
}