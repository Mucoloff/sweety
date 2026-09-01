package dev.sweety.saas.service.packet.global.monitoring.request;

import dev.sweety.data.buffer.*;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;

import java.util.HashMap;
import java.util.Map;

public class MonitoringMetricReportRequest extends PacketTransaction.Transaction {

    private ServiceType serviceType;
    private long timestamp;
    private Map<String, Long> metrics; // metric name -> value

    public MonitoringMetricReportRequest() {
    }

    public MonitoringMetricReportRequest(ServiceType serviceType, Map<String, Long> metrics) {
        this.serviceType = serviceType;
        this.metrics = metrics;
    }

    public MonitoringMetricReportRequest(ServiceType serviceType, long timestamp, Map<String, Long> metrics) {
        this.serviceType = serviceType;
        this.timestamp = timestamp;
        this.metrics = metrics;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeObject(serviceType);
        buffer.writeVarLong(System.currentTimeMillis());
        buffer.writeMap(metrics, BufferWriter::writeString, BufferWriter::writeVarLong);
    }

    @Override
    public void read(BufferReader buffer) {
        this.serviceType = buffer.readObject(ServiceType.DECODER);
        this.timestamp = buffer.readVarLong();
        this.metrics = buffer.readMap(BufferReader::readString, BufferReader::readVarLong, HashMap::new);
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
