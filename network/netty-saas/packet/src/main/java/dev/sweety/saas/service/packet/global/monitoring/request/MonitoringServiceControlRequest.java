package dev.sweety.saas.service.packet.global.monitoring.request;

import dev.sweety.data.buffer.*;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;

public class MonitoringServiceControlRequest extends PacketTransaction.Transaction {

    public enum Action {
        RESTART,
        STOP,
        START,
        STATUS
    }

    private ServiceType serviceType;
    private Action action;

    public MonitoringServiceControlRequest() {
    }

    public MonitoringServiceControlRequest(ServiceType serviceType, Action action) {
        this.serviceType = serviceType;
        this.action = action;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeObject(serviceType);
        buffer.writeEnum(action);
    }

    @Override
    public void read(BufferReader buffer) {
        this.serviceType = buffer.readObject(ServiceType.DECODER);
        this.action = buffer.readEnum(Action.class);
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public MonitoringServiceControlRequest setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public Action action() {
        return action;
    }

    public MonitoringServiceControlRequest setAction(Action action) {
        this.action = action;
        return this;
    }
}
