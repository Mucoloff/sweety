package dev.sweety.netty.messaging.model;

import dev.sweety.netty.messaging.transport.TcpTransport;
import dev.sweety.netty.messaging.transport.Transport;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.time.TimeMode;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Messenger {

    // ==============================/
    // Handlers are now created per-connection instead of shared
    // ==============================/
    private final AbstractBootstrap<?, ?> bootstrap;
    private final Transport transport;
    private final boolean server;
    private final NioEventLoopGroup boss, worker;
    // ===================================/
    public static final int SEED = 0x000FFFFF;

    @ChannelHandler.Sharable
    private static final class IdleDisconnectHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                ctx.close();
            } else {
                ctx.fireUserEventTriggered(evt);
            }
        }
    }

    private static final IdleDisconnectHandler IDLE_DISCONNECT_HANDLER = new IdleDisconnectHandler();

    // Real backpressure on top of WRITE_BUFFER_WATER_MARK: the watermark alone is only advisory —
    // Netty still queues every write() unbounded past the high mark unless the caller stops issuing
    // them. writePacket() below holds outgoing packets here instead of writing while !isWritable(),
    // and this handler drains the queue once writability returns. Per-channel state, since the
    // handler instance is @Sharable across every connection.
    /** A deferred write plus the future that must eventually resolve, one way or another. */
    private record PendingWrite(Runnable writeAction, CompletableFuture<?> future) {}

    private static final AttributeKey<ArrayDeque<PendingWrite>> PENDING_WRITES = AttributeKey.valueOf("luce-pending-writes");
    private static final AttributeKey<Long> UNWRITABLE_SINCE = AttributeKey.valueOf("luce-unwritable-since");
    private static final int MAX_PENDING_WRITES = Integer.parseInt(System.getenv().getOrDefault("NETTY_MAX_PENDING_WRITES", "1024"));
    private static final long MAX_UNWRITABLE_MILLIS = Long.parseLong(System.getenv().getOrDefault("NETTY_MAX_UNWRITABLE_MILLIS", "15000"));

    /** Queues a write while the channel is backed up, or runs it immediately if not. Must run on the event loop. */
    private static void enqueueOrWrite(ChannelHandlerContext ctx, Runnable writeAction, CompletableFuture<?> future) {
        Channel channel = ctx.channel();
        if (channel.isWritable()) {
            writeAction.run();
            return;
        }

        Long since = channel.attr(UNWRITABLE_SINCE).get();
        if (since == null) {
            channel.attr(UNWRITABLE_SINCE).set(System.currentTimeMillis());
        }

        ArrayDeque<PendingWrite> queue = channel.attr(PENDING_WRITES).get();
        if (queue == null) {
            queue = new ArrayDeque<>();
            channel.attr(PENDING_WRITES).set(queue);
        }

        // Peer has been backed up too long, or is backed up so deep it'll never realistically drain —
        // treat it as dead rather than let the pending queue itself become the OOM vector. channelInactive
        // (fired once close() completes) drains and fails whatever was already queued; this packet never
        // made it in, so fail it here directly.
        if ((since != null && System.currentTimeMillis() - since > MAX_UNWRITABLE_MILLIS) || queue.size() >= MAX_PENDING_WRITES) {
            future.completeExceptionally(new IllegalStateException("Channel backpressure limit exceeded, treating peer as dead"));
            channel.close();
            return;
        }

        queue.add(new PendingWrite(writeAction, future));
    }

    @ChannelHandler.Sharable
    private static final class WritabilityDrainHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            Channel channel = ctx.channel();
            if (channel.isWritable()) {
                channel.attr(UNWRITABLE_SINCE).set(null);
                ArrayDeque<PendingWrite> queue = channel.attr(PENDING_WRITES).get();
                if (queue != null) {
                    PendingWrite pending;
                    while (channel.isWritable() && (pending = queue.poll()) != null) {
                        pending.writeAction().run();
                    }
                }
            }
            ctx.fireChannelWritabilityChanged();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // Channel closed (for any reason) with packets still queued behind backpressure — fail
            // them now instead of leaving their futures pending forever.
            ArrayDeque<PendingWrite> queue = ctx.channel().attr(PENDING_WRITES).getAndSet(null);
            if (queue != null) {
                PendingWrite pending;
                while ((pending = queue.poll()) != null) {
                    pending.future().completeExceptionally(new IllegalStateException("Channel closed while packet was queued"));
                }
            }
            super.channelInactive(ctx);
        }
    }

    private static final WritabilityDrainHandler WRITABILITY_DRAIN_HANDLER = new WritabilityDrainHandler();

    /**
     * Installs the slowloris idle-disconnect guard (if enabled) and the backpressure-drain handler.
     * Stream-transport-only plumbing, called from {@code TcpTransport.installCodecs}; a connectionless
     * transport (UDP) has no meaningful idle/backpressure notion on a single shared channel.
     */
    public static void installIdleAndBackpressure(ChannelPipeline pipeline, int idleTimeoutSeconds) {
        if (idleTimeoutSeconds > 0) {
            pipeline.addLast("idleState", new IdleStateHandler(idleTimeoutSeconds, 0, 0, TimeUnit.SECONDS));
            pipeline.addLast("idleDisconnect", IDLE_DISCONNECT_HANDLER);
        }
        pipeline.addLast("writabilityDrain", WRITABILITY_DRAIN_HANDLER);
    }

    protected Channel channel;

    protected int port;
    protected String host;

    public int port() {
        return port;
    }

    public Messenger port(int port) {
        this.port = port;
        return this;
    }

    public String host() {
        return host;
    }

    public Messenger host(String host) {
        this.host = host;
        return this;
    }

    private final AtomicBoolean running = new AtomicBoolean();

    public static TimeMode timeMode = TimeMode.MILLIS;

    private final PacketRegistry packetRegistry;

    public PacketRegistry packetRegistry() {
        return packetRegistry;
    }

    protected Messenger(String host, int port, PacketRegistry packetRegistry, int localPort) {
        this(TcpTransport.INSTANCE, false, host, port, packetRegistry, localPort);
    }

    protected Messenger(boolean server, String host, int port, PacketRegistry packetRegistry, int localPort) {
        this(TcpTransport.INSTANCE, server, host, port, packetRegistry, localPort);
    }

    protected Messenger(Transport transport, boolean server, String host, int port, PacketRegistry packetRegistry, int localPort) {
        this.transport = transport;
        this.server = server;
        this.bootstrap = transport.newBootstrap(server);
        this.boss = new NioEventLoopGroup();
        final int worker_threads = Integer.parseInt(
                System.getenv().getOrDefault("NETTY_WORKER_THREADS",
                        String.valueOf(Math.max(4, Runtime.getRuntime().availableProcessors() * 2))));
        final int so_backlog = Integer.parseInt(System.getenv().getOrDefault("NETTY_SO_BACKLOG", "256"));
        // OOM guard: without this, a slow/malicious reader on the other end never applies backpressure —
        // writes just keep queuing in the outbound buffer with no ceiling. High mark pauses writability,
        // low mark resumes it once drained.
        final WriteBufferWaterMark waterMark = new WriteBufferWaterMark(
                Integer.parseInt(System.getenv().getOrDefault("NETTY_WRITE_BUFFER_LOW_WATER_MARK", String.valueOf(1 << 20))),
                Integer.parseInt(System.getenv().getOrDefault("NETTY_WRITE_BUFFER_HIGH_WATER_MARK", String.valueOf(4 << 20))));
        // Slowloris guard: a connection sending nothing (or trickling bytes) ties up its fd + event-loop
        // slot indefinitely otherwise. 0 disables the check.
        final int idleTimeoutSeconds = Integer.parseInt(System.getenv().getOrDefault("NETTY_IDLE_TIMEOUT_SECONDS", "60"));
        this.worker = new NioEventLoopGroup(worker_threads);

        this.port = port;
        this.host = host;
        this.packetRegistry = packetRegistry;

        final ChannelInitializer<Channel> init = new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline p = ch.pipeline();
                Messenger.this.configurePipeline(p);
                transport.installCodecs(p, Messenger.this.packetRegistry, Messenger.this, idleTimeoutSeconds);
            }
        };

        transport.configure(this.bootstrap, server, this.boss, this.worker, so_backlog, waterMark, 10000, init, localPort);
    }


    public Channel start() {
        return this.connect().join();
    }

    private Consumer<Channel> onConnect;

    public Consumer<Channel> onConnect() {
        return onConnect;
    }

    public Messenger onConnect(Consumer<Channel> onConnect) {
        this.onConnect = onConnect;
        return this;
    }

    /** {@code true} for TCP (stream, per-connection lifecycle); {@code false} for UDP (shared datagram channel). */
    protected final boolean connectionOriented() {
        return transport.connectionOriented();
    }

    public CompletableFuture<Channel> connect() {
        final CompletableFuture<Channel> future = new CompletableFuture<>();

        ChannelFutureListener channelFutureListener = (f) -> {
            if (f.isSuccess()) {
                future.complete(this.channel = f.channel());
                if (this.onConnect != null) onConnect.accept(this.channel);
            } else {
                future.completeExceptionally(f.cause());
            }
        };

        final ChannelFuture channelFuture;
        try {
            channelFuture = transport.start(this.bootstrap, this.server, this.host, this.port);
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }
        channelFuture.addListener(channelFutureListener);

        return future;
    }

    public <T> CompletableFuture<T> sendPacket(ChannelHandlerContext ctx, Packet packet) {
        CompletableFuture<T> future = writePacket(ctx, packet);
        flush(ctx);
        return future;
    }

    public <T> CompletableFuture<T> sendPacket(ChannelHandlerContext ctx, Packet... msgs) {
        if (msgs == null || msgs.length == 0) return CompletableFuture.completedFuture(null);
        if (msgs.length == 1) return sendPacket(ctx, msgs[0]);
        CompletableFuture<T> future = writePacket(ctx, msgs);
        flush(ctx);
        return future;
    }

    public <T> CompletableFuture<T> writePacket(ChannelHandlerContext ctx, Packet packet) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (ctx == null || !ctx.channel().isActive()) {
            future.completeExceptionally(new IllegalStateException("Channel not active or context is null"));
            return future;
        }
        // Gate the actual write behind writability instead of always calling channel.write() —
        // otherwise WRITE_BUFFER_WATER_MARK is purely advisory and a backed-up peer still grows
        // Netty's internal queue without bound. enqueueOrWrite must run on the event loop (it touches
        // per-channel queue state), hence safeRun.
        safeRun(ctx, c -> enqueueOrWrite(c, () -> {
            onPacketSend(c, packet, true);
            c.channel().write(packet).addListener(f -> {
                if (f.isSuccess()) {
                    onPacketSend(c, packet, false);
                    future.complete(null);
                } else {
                    future.completeExceptionally(f.cause());
                }
            });
        }, future));
        return future;
    }

    public <T> CompletableFuture<T> writePacket(ChannelHandlerContext ctx, Packet... msgs) {
        if (msgs == null || msgs.length == 0) return CompletableFuture.completedFuture(null);
        if (msgs.length == 1) return writePacket(ctx, msgs[0]);

        CompletableFuture<T> lastWrite = null;
        for (Packet packet : msgs) {
            lastWrite = writePacket(ctx, packet);
        }
        return lastWrite != null ? lastWrite : CompletableFuture.completedFuture(null);
    }

    public void flush(ChannelHandlerContext ctx) {
        if (ctx != null && ctx.channel().isActive()) {
            ctx.channel().flush();
        }
    }

    public static <T> CompletableFuture<T> safeExecute(ChannelHandlerContext ctx, Function<ChannelHandlerContext, CompletableFuture<T>> function) {
        //noinspection resource
        final EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) return function.apply(ctx);

        final CompletableFuture<T> future = new CompletableFuture<>();

        executor.execute(() -> {
            final CompletableFuture<T> internal = function.apply(ctx);
            if (internal == null) {
                future.complete(null);
                return;
            }
            internal.whenComplete((v, t) -> {
                if (t != null) future.completeExceptionally(t);
                else future.complete(v);
            });
        });

        return future;
    }

    public static void safeRun(ChannelHandlerContext ctx, Consumer<ChannelHandlerContext> action) {
        //noinspection resource
        final EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) action.accept(ctx);
        else executor.execute(() -> action.accept(ctx));
    }

    public static <M extends Messenger> void init(M messenger) {
        final CountDownLatch latch = new CountDownLatch(1);

        messenger.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                messenger.stop();
            } finally {
                latch.countDown();
            }
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String address(Channel channel) {
        return "remote[%s] local[%s]".formatted(channel.remoteAddress(), channel.localAddress());
    }

    public void stop() {
        this.boss.shutdownGracefully();
        this.worker.shutdownGracefully();
    }

    protected void configurePipeline(ChannelPipeline pipeline) {
    }

    public abstract void onPacketReceive(ChannelHandlerContext ctx, Packet packet);

    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, boolean pre) {
    }

    public abstract void exception(ChannelHandlerContext ctx, Throwable throwable);

    public abstract void join(ChannelHandlerContext ctx, ChannelPromise promise);

    public abstract void quit(ChannelHandlerContext ctx, ChannelPromise promise);

}
