package dev.sweety.saas.service.packet.global.handshake;

import dev.sweety.netty.packet.model.PacketTransaction;

import java.util.Optional;

public class SystemConnectionTransaction extends PacketTransaction<SystemConnection, SystemConnection> {

    public SystemConnectionTransaction() {
        super();
    }

    public SystemConnectionTransaction(SystemConnection request) {
        super(request);
    }

    public SystemConnectionTransaction(long id, SystemConnection response) {
        super(id, response);
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
