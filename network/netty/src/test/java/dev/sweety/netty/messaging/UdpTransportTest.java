package dev.sweety.netty.messaging;

import dev.sweety.cache.IpAddress;
import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.messaging.transport.UdpTransport;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketRegistry;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class UdpTransportTest {

    public static class PingPacket extends Packet {
        private String message;

        public PingPacket() {}

        public PingPacket(String message) {
            this.message = message;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeString(message != null ? message : "");
        }

        @Override
        public void read(BufferReader buffer) {
            this.message = buffer.readString();
        }

        public String message() { return message; }
    }

    public static class PongPacket extends Packet {
        private String reply;

        public PongPacket() {}

        public PongPacket(String reply) {
            this.reply = reply;
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeString(reply != null ? reply : "");
        }

        @Override
        public void read(BufferReader buffer) {
            this.reply = buffer.readString();
        }

        public String reply() { return reply; }
    }

    @Test
    public void testAddressedPacketPooling() {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 9999);
        PingPacket ping = new PingPacket("pool-test");

        AddressedPacket p1 = AddressedPacket.acquire(ping, addr);
        assertSame(ping, p1.packet());
        assertEquals(addr, p1.recipient());

        p1.release(); // recycled

        AddressedPacket p2 = AddressedPacket.acquire(ping, addr);
        assertSame(p1, p2); // exact same instance reused from thread-local pool!
        p2.release();
    }

    @Test
    public void testIpAddressCodec() throws Exception {
        InetAddress localIpv4 = InetAddress.getByName("127.0.0.1");
        IpAddress ip = IpAddress.from(localIpv4);
        assertEquals("127.0.0.1", ip.getAddress());

        PacketBuffer buffer = new PacketBuffer();
        ip.write(buffer);

        IpAddress read = new IpAddress();
        read.read(buffer);
        assertEquals("127.0.0.1", read.getAddress());

        InetSocketAddress sock = ip.toInetSocketAddress(8080);
        assertEquals(8080, sock.getPort());
        assertEquals("127.0.0.1", sock.getHostString());
    }

    @Test
    public void testUdpPacketTransmissionAndSenderPreservation() throws Exception {
        PacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(0, PingPacket.class);
        registry.registerPacket(1, PongPacket.class);

        int serverPort = 14567;
        CompletableFuture<AddressedPacket> serverReceived = new CompletableFuture<>();

        SimpleServer server = new SimpleServer(UdpTransport.packets(), "127.0.0.1", serverPort, registry) {
            @Override
            public void onPacketReceive(io.netty.channel.ChannelHandlerContext ctx, Packet packet) {
                if (packet instanceof AddressedPacket addressed) {
                    serverReceived.complete(addressed);
                }
            }
        };

        server.start();

        SimpleClient client = new SimpleClient(UdpTransport.unconnected(), "127.0.0.1", serverPort, registry, -1) {
            @Override
            public void onPacketReceive(io.netty.channel.ChannelHandlerContext ctx, Packet packet) {
            }
        };

        try {
            client.start();
            InetSocketAddress serverAddr = new InetSocketAddress("127.0.0.1", serverPort);
            client.sendPacket(new AddressedPacket(new PingPacket("hello-udp"), serverAddr));

            AddressedPacket receivedOnServer = serverReceived.get(3, TimeUnit.SECONDS);
            assertNotNull(receivedOnServer);
            assertInstanceOf(PingPacket.class, receivedOnServer.packet());
            assertEquals("hello-udp", ((PingPacket) receivedOnServer.packet()).message());
            assertNotNull(receivedOnServer.sender());
            assertEquals("127.0.0.1", receivedOnServer.sender().getAddress().getHostAddress());
        } finally {
            client.stop();
            server.stop();
        }
    }
}
