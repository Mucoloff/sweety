package dev.sweety.network.cloud.packet.model;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;
import dev.sweety.network.cloud.packet.incoming.CallableDecoder;
import dev.sweety.network.cloud.packet.outgoing.CallableEncoder;
import dev.sweety.network.cloud.packet.outgoing.Encoder;
import dev.sweety.persistence.config.format.Format;

public enum Bound implements Encoder{
    C2S, S2C;
    public static final Bound[] VALUES = values();

    public void write(PacketBuffer buffer){
        buffer.writeBoolean(this == C2S);
    }

    public static final CallableEncoder<Bound> encoder = Bound::write;

    public static final CallableDecoder<Bound> decoder = (buffer) -> buffer.readBoolean() ? S2C : C2S;

}
