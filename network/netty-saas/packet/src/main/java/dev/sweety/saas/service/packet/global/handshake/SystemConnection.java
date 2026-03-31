package dev.sweety.saas.service.packet.global.handshake;


import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SystemConnection extends PacketTransaction.Transaction {

    public enum State {
        REQUEST,
        RESPONSE,
        TIMEOUT
    }

    private ServiceType serviceType;
    private State state;

    public SystemConnection(ServiceType serviceType, State state) {
        this.serviceType = serviceType;
        this.state = state;
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeObject(serviceType);
        buffer.writeEnum(state);
    }

    @Override
    public void read(PacketBuffer buffer) {
        this.serviceType = buffer.readObject(ServiceType.DECODER);
        this.state = buffer.readEnum(State.class);
    }

    public boolean request() {
        return state == State.REQUEST;
    }

    public boolean response() {
        return state == State.RESPONSE;
    }

    public boolean timeout() {
        return state == State.TIMEOUT;
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public SystemConnection setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public State state() {
        return state;
    }

    public SystemConnection setState(State state) {
        this.state = state;
        return this;
    }
}
