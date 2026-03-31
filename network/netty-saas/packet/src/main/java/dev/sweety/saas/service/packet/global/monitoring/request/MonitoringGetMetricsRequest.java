package dev.sweety.saas.service.packet.global.monitoring.request;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class MonitoringGetMetricsRequest extends PacketTransaction.Transaction {

    private ServiceType serviceType;
    private long timeRange;

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeObject(this.serviceType);
        buffer.writeVarLong(this.timeRange);
    }

    @Override
    public void read(PacketBuffer buffer) {
        this.serviceType = buffer.readObject(ServiceType.DECODER);
        this.timeRange = buffer.readVarLong();
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public MonitoringGetMetricsRequest setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public long timeRange() {
        return timeRange;
    }

    public MonitoringGetMetricsRequest setTimeRange(long timeRange) {
        this.timeRange = timeRange;
        return this;
    }
}
