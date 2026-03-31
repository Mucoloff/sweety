package dev.sweety.saas.service.packet.global.handshake;


import dev.sweety.netty.packet.model.PacketTransaction;

import java.util.Optional;

public class SystemConnectionTransaction extends PacketTransaction<SystemConnection, SystemConnection> {

    public SystemConnectionTransaction(SystemConnection request) {
        super(request);
    }

    public SystemConnectionTransaction(long id, SystemConnection response) {
        super(id, response);
    }

    public SystemConnectionTransaction(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @Override
    protected SystemConnection request() {
        return new SystemConnection();
    }

    @Override
    protected SystemConnection response() {
        return new SystemConnection();
    }

    public Optional<SystemConnection> get() {
        final SystemConnection val = hasRequest() ? getRequest() : hasResponse() ? getResponse() : null;
        return Optional.ofNullable(val);
    }

}
