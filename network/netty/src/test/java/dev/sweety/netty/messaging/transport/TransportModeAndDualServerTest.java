package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.packet.annotation.TransportHint;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TransportModeAndDualServerTest {

    @TransportHint(TransportMode.UDP)
    public static class SampleUdpPacket extends Packet {
        @Override public void write(dev.sweety.data.buffer.BufferWriter buffer) {}
        @Override public void read(dev.sweety.data.buffer.BufferReader buffer) {}
    }

    @TransportHint(TransportMode.TCP)
    public static class SampleTcpPacket extends Packet {
        @Override public void write(dev.sweety.data.buffer.BufferWriter buffer) {}
        @Override public void read(dev.sweety.data.buffer.BufferReader buffer) {}
    }

    public static class DefaultPacket extends Packet {
        @Override public void write(dev.sweety.data.buffer.BufferWriter buffer) {}
        @Override public void read(dev.sweety.data.buffer.BufferReader buffer) {}
    }

    @Test
    public void testTransportModeBitmask() {
        assertTrue(TransportMode.TCP.hasTcp());
        assertFalse(TransportMode.TCP.hasUdp());

        assertFalse(TransportMode.UDP.hasTcp());
        assertTrue(TransportMode.UDP.hasUdp());

        assertTrue(TransportMode.DUAL.hasTcp());
        assertTrue(TransportMode.DUAL.hasUdp());
    }

    @Test
    public void testOptimizedPacketRegistryTransportIndexing() throws Exception {
        OptimizedPacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(1, SampleUdpPacket.class);
        registry.registerPacket(2, SampleTcpPacket.class);
        registry.registerPacket(3, DefaultPacket.class);
        registry.trim();

        assertEquals(TransportMode.FLAG_UDP, registry.getTransportMode(1));
        assertEquals(TransportMode.FLAG_TCP, registry.getTransportMode(2));
        assertEquals(TransportMode.FLAG_TCP, registry.getTransportMode(3), "Default hint should be TCP");
    }

    @Test
    public void testDualClientAutoRouting() throws Exception {
        OptimizedPacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(1, SampleUdpPacket.class);
        registry.registerPacket(2, SampleTcpPacket.class);
        registry.trim();

        int port = 18991;
        java.util.concurrent.CompletableFuture<Boolean> udpHit = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Boolean> tcpHit = new java.util.concurrent.CompletableFuture<>();

        dev.sweety.netty.messaging.impl.DualServer server = new dev.sweety.netty.messaging.impl.DualServer("127.0.0.1", port, registry) {
            @Override
            public void onPacketReceive(io.netty.channel.ChannelHandlerContext ctx, Packet packet) {
                if (packet instanceof AddressedPacket addressed) {
                    if (addressed.packet() instanceof SampleUdpPacket) {
                        udpHit.complete(true);
                    }
                } else if (packet instanceof SampleTcpPacket) {
                    tcpHit.complete(true);
                }
            }
        };

        dev.sweety.netty.messaging.impl.DualClient client = new dev.sweety.netty.messaging.impl.DualClient("127.0.0.1", port, registry) {
            @Override
            public void onPacketReceive(io.netty.channel.ChannelHandlerContext ctx, Packet packet) {}
        };

        try {
            server.start();
            client.start();

            // Send via auto-routing
            client.sendPacket(new SampleUdpPacket());
            client.sendPacket(new SampleTcpPacket());

            assertTrue(udpHit.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(tcpHit.get(3, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            client.stop();
            server.stop();
        }
    }
}
