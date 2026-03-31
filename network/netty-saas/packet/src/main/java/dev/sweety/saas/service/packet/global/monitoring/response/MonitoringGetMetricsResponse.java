package dev.sweety.saas.service.packet.global.monitoring.response;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.ServiceType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
public class MonitoringGetMetricsResponse extends PacketTransaction.Transaction {

    private Map<ServiceType, Map<String, Long>> allMetrics; // serviceType -> (metricName -> value)

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeMap(allMetrics, PacketBuffer::writeObject, (buf, map) -> {
            buf.writeMap(map, PacketBuffer::writeString, PacketBuffer::writeVarLong);
        });
    }

    @Override
    public void read(PacketBuffer buffer) {
        this.allMetrics = buffer.readMap(
                buf -> buf.readObject(ServiceType.DECODER),
                buf -> buf.readMap(PacketBuffer::readString, PacketBuffer::readVarLong, HashMap::new),
                HashMap::new
        );
    }

    public Map<ServiceType, Map<String, Long>> allMetrics() {
        return allMetrics;
    }

    public MonitoringGetMetricsResponse setAllMetrics(Map<ServiceType, Map<String, Long>> allMetrics) {
        this.allMetrics = allMetrics;
        return this;
    }
}
