package dev.sweety.netty;

import dev.sweety.netty.messaging.impl.GenericClient;
import dev.sweety.netty.messaging.impl.GenericServer;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.annotation.TransportHint;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class NettyFluentDslTest {

    @TransportHint(TransportMode.UDP)
    public static class Ping extends Packet {
        @Override public void write(dev.sweety.data.buffer.BufferWriter buffer) {}
        @Override public void read(dev.sweety.data.buffer.BufferReader buffer) {}
    }

    @TransportHint(TransportMode.TCP)
    public static class Echo extends Packet {
        @Override public void write(dev.sweety.data.buffer.BufferWriter buffer) {}
        @Override public void read(dev.sweety.data.buffer.BufferReader buffer) {}
    }

    @Test
    public void testFluentDualServerAndClient() throws Exception {
        OptimizedPacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(1, Ping.class);
        registry.registerPacket(2, Echo.class);
        registry.trim();

        int port = 19445;
        CompletableFuture<Boolean> receivedPing = new CompletableFuture<>();
        CompletableFuture<Boolean> receivedEcho = new CompletableFuture<>();

        GenericServer server = Netty.dualServer("127.0.0.1", port, registry)
                .onReceive((ctx, packet) -> {
                    if (packet instanceof dev.sweety.netty.messaging.transport.AddressedPacket addressed) {
                        if (addressed.packet() instanceof Ping) {
                            receivedPing.complete(true);
                        }
                    } else if (packet instanceof Echo) {
                        receivedEcho.complete(true);
                    }
                })
                .build();

        GenericClient client = Netty.dualClient("127.0.0.1", port, registry)
                .build();

        try {
            server.start();
            client.start();

            client.sendPacket(new Ping());
            client.sendPacket(new Echo());

            assertTrue(receivedPing.get(3, TimeUnit.SECONDS), "UDP packet should be received via dual routing");
            assertTrue(receivedEcho.get(3, TimeUnit.SECONDS), "TCP packet should be received via dual routing");
        } finally {
            client.stop();
            server.stop();
        }
    }
}
