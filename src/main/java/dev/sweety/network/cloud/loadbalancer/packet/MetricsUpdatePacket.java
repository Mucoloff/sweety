package dev.sweety.network.cloud.loadbalancer.packet;

import dev.sweety.network.cloud.impl.PacketRegistry;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.model.IPacket;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import lombok.Getter;

public interface MetricsUpdatePacket extends IPacket {

    @Getter
    class In extends PacketIn implements MetricsUpdatePacket {
        private final double cpuLoad;
        private final double ramUsage; // Percentuale di RAM usata (0.0 a 1.0)

        public In(PacketIn packet) {
            super(packet);
            this.cpuLoad = this.buffer.readDouble();
            this.ramUsage = this.buffer.readDouble();
        }
    }

    class Out extends PacketOut implements MetricsUpdatePacket {
        public Out(double cpuLoad, double ramUsage) {
            super(PacketRegistry.METRICS.id());
            this.buffer.writeDouble(cpuLoad);
            this.buffer.writeDouble(ramUsage);
        }
    }
}
