package dev.sweety.saas.service.packet.global.monitoring.response;

import dev.sweety.data.buffer.*;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class MonitoringMetricReportResponse extends PacketTransaction.Transaction {

    private boolean success;

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeBoolean(success);
    }

    @Override
    public void read(BufferReader buffer) {
        this.success = buffer.readBoolean();
    }

    public boolean success() {
        return success;
    }

    public MonitoringMetricReportResponse setSuccess(boolean success) {
        this.success = success;
        return this;
    }
}
