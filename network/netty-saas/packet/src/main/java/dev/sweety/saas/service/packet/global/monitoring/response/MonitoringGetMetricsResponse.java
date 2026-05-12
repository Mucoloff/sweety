package dev.sweety.saas.service.packet.global.monitoring.response;

import dev.sweety.data.buffer.*;
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
    public void write(BufferWriter buffer) {
        buffer.writeMap(allMetrics, BufferWriter::writeObject, (buf, map) -> {
            buf.writeMap(map, BufferWriter::writeString, BufferWriter::writeVarLong);
        });
    }

    @Override
    public void read(BufferReader buffer) {
        this.allMetrics = buffer.readMap(
                buf -> buf.readObject(ServiceType.DECODER),
                buf -> buf.readMap(BufferReader::readString, BufferReader::readVarLong, HashMap::new),
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
