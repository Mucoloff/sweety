package dev.sweety.saas.service.packet.global.monitoring.request;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
public class MonitoringMetricReportRequest extends PacketTransaction.Transaction {

    private ServiceType serviceType;
    private long timestamp;
    private Map<String, Long> metrics; // metric name -> value

    public MonitoringMetricReportRequest(ServiceType serviceType, Map<String, Long> metrics) {
        this.serviceType = serviceType;
        this.metrics = metrics;
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeObject(serviceType);
        buffer.writeVarLong(System.currentTimeMillis());
        buffer.writeMap(metrics, PacketBuffer::writeString, PacketBuffer::writeVarLong);
    }

    @Override
    public void read(PacketBuffer buffer) {
        this.serviceType = buffer.readObject(ServiceType.DECODER);
        this.timestamp = buffer.readVarLong();
        this.metrics = buffer.readMap(PacketBuffer::readString, PacketBuffer::readVarLong, HashMap::new);
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public MonitoringMetricReportRequest setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public long timestamp() {
        return timestamp;
    }

    public MonitoringMetricReportRequest setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public Map<String, Long> metrics() {
        return metrics;
    }

    public MonitoringMetricReportRequest setMetrics(Map<String, Long> metrics) {
        this.metrics = metrics;
        return this;
    }
}
