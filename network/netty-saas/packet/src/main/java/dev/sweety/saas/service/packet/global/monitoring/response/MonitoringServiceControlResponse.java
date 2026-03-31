package dev.sweety.saas.service.packet.global.monitoring.response;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.PacketTransaction;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class MonitoringServiceControlResponse extends PacketTransaction.Transaction {

    private boolean success;
    private String message;

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(success);
        buffer.writeString(message);
    }

    @Override
    public void read(PacketBuffer buffer) {
        this.success = buffer.readBoolean();
        this.message = buffer.readString();
    }

    public boolean success() {
        return success;
    }

    public MonitoringServiceControlResponse setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String message() {
        return message;
    }

    public MonitoringServiceControlResponse setMessage(String message) {
        this.message = message;
        return this;
    }
}
