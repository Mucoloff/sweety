package dev.sweety.network.cloud.messaging;

import dev.sweety.network.cloud.messaging.model.Messenger;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import io.netty.bootstrap.Bootstrap;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class Client extends Messenger<Bootstrap> {
    public Client(String host, int port, PacketOut... packets) {
        super(new Bootstrap(), host, port, packets);
    }

    private final AtomicInteger pendingWrites = new AtomicInteger(0);

    public void sendPackets(PacketOut... packets) {
        if (this.channel != null && this.channel.isActive()) {
            for (PacketOut packet : packets) {
                this.channel.write(packet);
            }

            this.channel.flush();
        }
    }

    public void sendPacket(PacketOut packet) {
        if (this.channel != null && this.channel.isActive()) {
            this.channel.writeAndFlush(packet);
        }
    }

    public void writePacket(PacketOut packet) {
        if (channel != null && channel.isActive()) {
            pendingWrites.incrementAndGet();
            channel.write(packet).addListener(f -> pendingWrites.decrementAndGet());
        }
    }

    public void writePackets(PacketOut... packets) {
        if (this.channel != null && this.channel.isActive()) {
            for (PacketOut packet : packets) {
                pendingWrites.incrementAndGet();
                channel.write(packet).addListener(f -> pendingWrites.decrementAndGet());
            }

        }
    }

    public void flush() {
        if (!hasPendingWrites()) return;
        if (this.channel != null && this.channel.isActive()) {
            this.channel.flush();
        }
    }

    public boolean hasPendingWrites() {
        return pendingWrites.get() > 0;
    }
}
