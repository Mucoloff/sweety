package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.TriFunction;
import dev.sweety.core.math.vector.queue.LinkedQueue;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.loadbalancer.PacketQueue;
import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.listener.decoder.PacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.PacketEncoder;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LBServer extends Server {

    private final NodePool backendPool;
    private final SimpleLogger logger = new SimpleLogger(LBServer.class);
    private final LinkedQueue<PacketQueue> pendingPackets = new LinkedQueue<>();

    private final TransactionManager transactionManager = new TransactionManager(this);

    private final PacketEncoder encoder;
    private final PacketDecoder decoder;

    private static final long REQUEST_TIMEOUT_SECONDS = 30L;

    public LBServer(String host, int port, NodePool backendPool, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);

        this.backendPool = backendPool;
        this.backendPool.pool().forEach(node -> node.setLoadBalancer(this));
        this.backendPool.initialize();

        this.encoder = new PacketEncoder(packetRegistry).noChecksum();
        this.decoder = new PacketDecoder(packetRegistry).noChecksum();
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        logger.push("receive", AnsiColor.CYAN_BRIGHT).info("packet", packet);
        pendingPackets.enqueue(new PacketQueue(packet.rewind(), ctx));
        drainPending();
        logger.pop();
    }

    private final Map<Long, ChannelHandlerContext> contexts = new ConcurrentHashMap<>();

    private void drainPending() {
        if (this.pendingPackets.isEmpty()) return;

        PacketQueue pq;
        while ((pq = pendingPackets.dequeue()) != null) {
            final Packet packet = pq.packet();
            final ChannelHandlerContext ctx = pq.ctx();

            Node backend = backendPool.nextBackend(packet, ctx);
            if (backend == null) {
                pendingPackets.enqueueFirst(pq);
                logger.push("queue").warn("No backend available. pending packets = " + pendingPackets.size()).pop();
                break;
            }

            final InternalPacket internal = new InternalPacket(new InternalPacket.Forward(getPacketRegistry()::getPacketId, packet));
            final long requestId = internal.getRequestId();

            transactionManager.registerRequest(internal, REQUEST_TIMEOUT_SECONDS * 500L).whenComplete(((response, throwable) -> {
                if (throwable != null) {
                    logger.push("transaction", AnsiColor.RED_BRIGHT).error(throwable).pop();
                    return;
                }

                TriFunction<Packet, Integer, Long, byte[]> constructor = (id, ts, data) -> {
                    try {
                        return getPacketRegistry().constructPacket(id, ts, data);
                    } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        logger.error(e);
                        throw new RuntimeException(e);
                    }
                };

                Packet[] responses = response.decode(constructor);

                Arrays.stream(responses).forEach(Packet::rewind);

                logger.push("transaction", AnsiColor.GREEN_BRIGHT).info("forwarded packet responses", responses).pop();

                sendPacket(ctx, responses).whenComplete((_void, t) -> {
                    if (t != null) {
                        logger.push("send", AnsiColor.RED_BRIGHT).error(t).pop();
                    } else {
                        logger.push("send", AnsiColor.GREEN_BRIGHT).info("responses sent successfully").pop();
                    }
                });

            }));

            backend.forward(internal);
            contexts.put(requestId, ctx);
        }
    }

    @Override
    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, boolean pre) {
        logger.push("sniffing", AnsiColor.PURPLE)
                .push(ctx.channel().remoteAddress() + "")
                .info(AnsiColor.PURPLE_BRIGHT, "packet", packet, " | ", (pre ? "pre" : "post"))
                .pop().pop();
    }

    public void complete(InternalPacket internal) {
        if (transactionManager.completeResponse(internal)) {
            logger.push(internal.requestCode(), AnsiColor.GREEN_BRIGHT).info("completed").pop();
        } else {
            logger.push(internal.requestCode(), AnsiColor.RED_BRIGHT).warn("no matching transaction found").pop();
        }
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("Join").info(ctx.channel().remoteAddress()).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.logger.push("quit").info(ctx.channel().remoteAddress()).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

}
