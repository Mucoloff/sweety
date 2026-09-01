package dev.sweety.saas.hub;

import dev.sweety.loadbalancer.packet.MetricUpdatePacket;
import dev.sweety.loadbalancer.packet.PingPacket;
import dev.sweety.loadbalancer.packet.PongPacket;
import dev.sweety.netty.messaging.transport.Peer;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.Channel;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Correlates an active TCP channel with a UDP peer under the same authenticated session.
 * Automatically routes ephemeral/telemetry traffic to UDP and critical state changes to TCP.
 */
public final class DualTransportSession {

    private final long sessionId;
    private final byte[] sessionToken;
    private volatile Channel tcpChannel;
    private volatile Peer udpPeer;

    private final AtomicLong lastPingNanos = new AtomicLong(System.nanoTime());
    private final AtomicLong rttLatencyNanos = new AtomicLong(0);
    private volatile MetricUpdatePacket latestMetrics;

    public DualTransportSession(long sessionId, byte[] sessionToken, Channel tcpChannel) {
        this.sessionId = sessionId;
        this.sessionToken = Objects.requireNonNull(sessionToken, "sessionToken");
        this.tcpChannel = tcpChannel;
    }

    public long sessionId() { return sessionId; }
    public byte[] sessionToken() { return sessionToken; }

    public Channel tcpChannel() { return tcpChannel; }
    public void setTcpChannel(Channel channel) { this.tcpChannel = channel; }

    public Peer udpPeer() { return udpPeer; }
    public void setUdpPeer(Peer peer) { this.udpPeer = peer; }

    public long rttLatencyNanos() { return rttLatencyNanos.get(); }
    public MetricUpdatePacket latestMetrics() { return latestMetrics; }

    public void recordPong(long clientSentNanos) {
        long rtt = System.nanoTime() - clientSentNanos;
        this.rttLatencyNanos.set(rtt);
        this.lastPingNanos.set(System.nanoTime());
    }

    public void recordMetrics(MetricUpdatePacket metrics) {
        this.latestMetrics = metrics;
    }

    /**
     * Smart routing: sends via UDP if fast-path telemetry/ping, or TCP if reliable.
     */
    public void send(Packet packet) {
        if (isFastPathPacket(packet)) {
            sendFast(packet);
        } else {
            sendReliable(packet);
        }
    }

    public void sendReliable(Packet packet) {
        Channel ch = tcpChannel;
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(packet);
        }
    }

    public void sendFast(Packet packet) {
        Peer peer = udpPeer;
        if (peer != null) {
            peer.send(packet);
        } else {
            // Fallback to TCP if UDP endpoint is not yet correlated
            sendReliable(packet);
        }
    }

    private boolean isFastPathPacket(Packet packet) {
        return packet instanceof PingPacket ||
               packet instanceof PongPacket ||
               packet instanceof MetricUpdatePacket;
    }
}
