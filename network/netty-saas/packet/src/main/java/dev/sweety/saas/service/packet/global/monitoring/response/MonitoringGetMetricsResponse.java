package dev.sweety.saas.service.packet.global.monitoring.response;

import dev.sweety.data.buffer.*;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;

import java.util.HashMap;
import java.util.Map;

public class MonitoringGetMetricsResponse extends PacketTransaction.Transaction {

    private ServiceType serviceType;
    private Map<String, Long> metrics;

    public MonitoringGetMetricsResponse() {
    }

    public MonitoringGetMetricsResponse(ServiceType serviceType, Map<String, Long> metrics) {
        this.serviceType = serviceType;
        this.metrics = metrics;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeObject(serviceType);
        buffer.writeMap(metrics, BufferWriter::writeString, BufferWriter::writeVarLong);
    }

    @Override
    public void read(BufferReader buffer) {
        this.serviceType = buffer.readObject(ServiceType.DECODER);
        this.metrics = buffer.readMap(BufferReader::readString, BufferReader::readVarLong, HashMap::new);
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public MonitoringGetMetricsResponse setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public Map<String, Long> metrics() {
        return metrics;
    }

    public MonitoringGetMetricsResponse setMetrics(Map<String, Long> metrics) {
        this.metrics = metrics;
        return this;
    }
}
