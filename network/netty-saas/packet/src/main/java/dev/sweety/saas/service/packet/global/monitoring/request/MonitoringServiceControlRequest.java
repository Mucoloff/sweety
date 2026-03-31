package dev.sweety.saas.service.packet.global.monitoring.request;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class MonitoringServiceControlRequest extends PacketTransaction.Transaction {

    private ServiceType targetServiceType;
    private ControlAction action;

    public enum ControlAction {
        ENABLE_MAINTENANCE,
        DISABLE_MAINTENANCE,
        RESTART,
        SHUTDOWN,
        BOOT,
        REBOOT
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeObject(this.targetServiceType);
        buffer.writeEnum(this.action);
    }

    @Override
    public void read(PacketBuffer buffer) {
        this.targetServiceType = buffer.readObject(ServiceType.DECODER);
        this.action = buffer.readEnum(ControlAction.class);
    }

    public ServiceType targetServiceType() {
        return targetServiceType;
    }

    public MonitoringServiceControlRequest setTargetServiceType(ServiceType targetServiceType) {
        this.targetServiceType = targetServiceType;
        return this;
    }

    public ControlAction action() {
        return action;
    }

    public MonitoringServiceControlRequest setAction(ControlAction action) {
        this.action = action;
        return this;
    }
}
