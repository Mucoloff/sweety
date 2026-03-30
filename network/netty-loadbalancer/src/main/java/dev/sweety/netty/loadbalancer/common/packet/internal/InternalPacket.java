package dev.sweety.netty.loadbalancer.common.packet.internal;

import dev.sweety.math.function.TriFunction;
import dev.sweety.netty.packet.model.PacketTransaction;

import java.util.Optional;

public class InternalPacket extends PacketTransaction<ForwardData, ForwardData> {

    public InternalPacket(final ForwardData request) {
        super(request);
    }

    public InternalPacket(final long id, final ForwardData response) {
        super(id, response);
    }

    public InternalPacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @Override
    protected ForwardData request() {
        return new ForwardData();
    }

    @Override
    protected ForwardData response() {
        return new ForwardData();
    }

    public Optional<ForwardData> get() {
        final ForwardData val = hasRequest() ? getRequest() : hasResponse() ? getResponse() : null;
        return Optional.ofNullable(val);
    }

}
