package dev.sweety.saas.hub;

import dev.sweety.loadbalancer.packet.MetricUpdatePacket;
import dev.sweety.loadbalancer.packet.PingPacket;
import dev.sweety.loadbalancer.packet.PongPacket;
import dev.sweety.netty.messaging.transport.Peer;
import dev.sweety.netty.messaging.transport.UdpPeer;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DualMeshRoutingTest {

    @Test
    public void testDualTransportRoutingSeparation() {
        EmbeddedChannel tcpChannel = new EmbeddedChannel();
        EmbeddedChannel udpChannel = new EmbeddedChannel();

        UdpPeer udpPeer = new UdpPeer(udpChannel, new InetSocketAddress("127.0.0.1", 9999));

        DualTransportSession session = new DualTransportSession(1001L, new byte[]{1, 2, 3}, tcpChannel);
        session.setUdpPeer(udpPeer);

        // 1. Send Ping (should route to UDP fast-path)
        PingPacket ping = PingPacket.of(System.nanoTime());
        session.send(ping);

        Object udpOutbound = udpChannel.readOutbound();
        assertNotNull(udpOutbound);
        assertTrue(udpOutbound instanceof dev.sweety.netty.messaging.transport.AddressedPacket);
        assertSame(ping, ((dev.sweety.netty.messaging.transport.AddressedPacket) udpOutbound).packet());
        assertNull(tcpChannel.readOutbound(), "Ping should NOT route to TCP");

        // 2. Send MetricUpdate (should route to UDP fast-path)
        MetricUpdatePacket metrics = MetricUpdatePacket.of(15.5, 1024L * 1024L, 20.0, 50, 2);
        session.send(metrics);

        Object metricsOutbound = udpChannel.readOutbound();
        assertNotNull(metricsOutbound);
        assertTrue(metricsOutbound instanceof dev.sweety.netty.messaging.transport.AddressedPacket);
        assertSame(metrics, ((dev.sweety.netty.messaging.transport.AddressedPacket) metricsOutbound).packet());

        // 3. Record Pong latency
        long startNanos = System.nanoTime();
        session.recordPong(startNanos - 15_000_000L); // 15ms simulated
        assertTrue(session.rttLatencyNanos() >= 15_000_000L);
    }
}
