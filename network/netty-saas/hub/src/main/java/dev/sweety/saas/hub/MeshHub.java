package dev.sweety.saas.hub;

import dev.sweety.loadbalancer.packet.HandshakePacket;
import dev.sweety.loadbalancer.packet.HandshakeResponsePacket;
import dev.sweety.loadbalancer.packet.MetricUpdatePacket;
import dev.sweety.loadbalancer.packet.PingPacket;
import dev.sweety.loadbalancer.packet.PongPacket;
import dev.sweety.netty.messaging.impl.DualServer;
import dev.sweety.netty.messaging.listener.PacketCodecSupport;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dual-Transport Mesh Hub that manages node sessions, telemetry streaming and reliable sync.
 */
public final class MeshHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeshHub.class);

    private final DualServer server;
    private final SessionRegistry sessionRegistry = new SessionRegistry();
    private final AtomicLong nextSessionId = new AtomicLong(1000);
    private final SecureRandom secureRandom = new SecureRandom();

    public MeshHub(int port) {
        dev.sweety.netty.packet.registry.OptimizedPacketRegistry registry = new dev.sweety.netty.packet.registry.OptimizedPacketRegistry();
        try {
            registry.registerPacket(1, HandshakePacket.class);
            registry.registerPacket(2, HandshakeResponsePacket.class);
            registry.registerPacket(3, PingPacket.class);
            registry.registerPacket(4, PongPacket.class);
            registry.registerPacket(5, MetricUpdatePacket.class);
            registry.trim();
        } catch (dev.sweety.netty.messaging.exception.PacketRegistrationException e) {
            LOGGER.error("Failed to register packets in MeshHub", e);
        }

        this.server = new DualServer("0.0.0.0", port, registry) {
            @Override
            public void onPacketReceive(io.netty.channel.ChannelHandlerContext ctx, dev.sweety.netty.packet.model.Packet packet) {
                handleIncomingPacket(ctx, packet);
            }
        };
    }

    private void handleIncomingPacket(io.netty.channel.ChannelHandlerContext ctx, dev.sweety.netty.packet.model.Packet packet) {
        if (packet instanceof HandshakePacket hp) {
            long sessionId = nextSessionId.incrementAndGet();
            byte[] token = new byte[16];
            secureRandom.nextBytes(token);

            DualTransportSession session = new DualTransportSession(sessionId, token, ctx.channel());
            sessionRegistry.register(session);

            ctx.writeAndFlush(HandshakeResponsePacket.of(true, sessionId, token, "Session established"));
            LOGGER.info("Node session {} registered (Client: {})", sessionId, hp.clientVersion());
        } else if (packet instanceof PingPacket ping) {
            ctx.writeAndFlush(PongPacket.of(ping.timestampNanos(), System.nanoTime()));
        } else if (packet instanceof MetricUpdatePacket metrics) {
            LOGGER.debug("Received metric update: CPU={}% TPS={}", metrics.cpuLoad(), metrics.tps());
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public SessionRegistry sessionRegistry() {
        return sessionRegistry;
    }

    public DualServer server() {
        return server;
    }
}
