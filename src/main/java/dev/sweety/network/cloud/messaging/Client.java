package dev.sweety.network.cloud.messaging;

import dev.sweety.network.cloud.messaging.model.Messenger;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class Client extends Messenger<Bootstrap> {
    public Client(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(new Bootstrap(), host, port, packetRegistry, packets);
    }

    private final AtomicInteger pendingWrites = new AtomicInteger(0);

    public void sendPackets(Packet... packets) {
        if (this.channel != null && this.channel.isActive()) {
            for (Packet packet : packets) {
                this.channel.write(packet);
            }

            this.channel.flush();
        }

    }

    public Channel channel() {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Channel not connected or inactive.");
        }
        return channel;
    }

    public void sendPacket(Packet packet) {
        if (this.channel != null && this.channel.isActive()) {
            this.channel.writeAndFlush(packet);
        }
    }

    public void writePacket(Packet packet) {
        if (channel != null && channel.isActive()) {
            pendingWrites.incrementAndGet();
            channel.write(packet).addListener(f -> pendingWrites.decrementAndGet());
        }
    }

    public void writePackets(Packet... packets) {
        if (this.channel != null && this.channel.isActive()) {
            for (Packet packet : packets) {
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
