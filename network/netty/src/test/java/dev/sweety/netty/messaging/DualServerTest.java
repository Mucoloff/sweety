package dev.sweety.netty.messaging;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.messaging.impl.DualServer;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.messaging.transport.AddressedPacket;
import dev.sweety.netty.messaging.transport.TcpTransport;
import dev.sweety.netty.messaging.transport.UdpTransport;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DualServerTest {

    public static class TestMsg extends Packet {
        private String text;

        public TestMsg() {}
        public TestMsg(String text) { this.text = text; }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeString(text != null ? text : "");
        }

        @Override
        public void read(BufferReader buffer) {
            this.text = buffer.readString();
        }

        public String text() { return text; }
    }

    @Test
    public void testDualServerTcpAndUdpOnSamePort() throws Exception {
        PacketRegistry registry = new OptimizedPacketRegistry();
        registry.registerPacket(0, TestMsg.class);

        int dualPort = 17890;
        CompletableFuture<String> tcpReceived = new CompletableFuture<>();
        CompletableFuture<String> udpReceived = new CompletableFuture<>();

        DualServer dualServer = new DualServer("127.0.0.1", dualPort, registry) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
                if (packet instanceof AddressedPacket addressed) {
                    // UDP datagram
                    if (addressed.packet() instanceof TestMsg msg) {
                        udpReceived.complete(msg.text());
                    }
                } else if (packet instanceof TestMsg msg) {
                    // TCP stream
                    tcpReceived.complete(msg.text());
                }
            }
        };

        dualServer.start();

        SimpleClient tcpClient = new SimpleClient(TcpTransport.INSTANCE, "127.0.0.1", dualPort, registry, -1) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {}
        };

        SimpleClient udpClient = new SimpleClient(UdpTransport.unconnected(), "127.0.0.1", dualPort, registry, -1) {
            @Override
            public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {}
        };

        try {
            tcpClient.start();
            udpClient.start();

            // Send TCP packet
            tcpClient.sendPacket(new TestMsg("hello-tcp"));

            // Send UDP datagram to exact same port!
            udpClient.sendPacket(new AddressedPacket(new TestMsg("hello-udp"), new InetSocketAddress("127.0.0.1", dualPort)));

            assertEquals("hello-tcp", tcpReceived.get(3, TimeUnit.SECONDS));
            assertEquals("hello-udp", udpReceived.get(3, TimeUnit.SECONDS));
        } finally {
            tcpClient.stop();
            udpClient.stop();
            dualServer.stop();
        }
    }
}
