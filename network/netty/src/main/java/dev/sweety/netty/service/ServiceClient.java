package dev.sweety.netty.service;

import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.packet.model.BatchPacket;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.thread.ProfileThread;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base for a mesh participant: dials the {@link HubServer}, self-identifies with its numeric service id
 * on every (re)connect, and exchanges {@link ServiceMessage}s addressed by service id. Subclasses handle
 * inbound requests by overriding {@link #handle(int, Packet)}. RPCs reuse the shared
 * {@link TransactionManager} for request/response correlation; the inherited {@code AutoReconnect}
 * (from {@link SimpleClient}) transparently re-dials and re-identifies.
 *
 * <p>The supplied registry MUST include {@link ServicePackets} plus every application packet exchanged.
 */
public abstract class ServiceClient extends SimpleClient {

    /** Default RPC timeout; override per-call with {@link #sendRequest(int, Packet, long)}. */
    public static final long DEFAULT_TIMEOUT_MS = 10_000L;

    /** Ping interval and dead-connection threshold (missed 3 in a row ≈ no pong for this long). */
    private static final long PING_INTERVAL_MS = 5_000L;
    private static final long PONG_TIMEOUT_MS = PING_INTERVAL_MS * 3;

    private final int serviceId;
    private final String secret;
    private final PacketRegistry registry;
    protected final TransactionManager transactions = new TransactionManager(this);
    private final ProfileThread heartbeat = new ProfileThread("service-client-heartbeat");
    private final AtomicLong lastPongAt = new AtomicLong();
    private volatile CompletableFuture<?> heartbeatTask;

    protected ServiceClient(String hubHost, int hubPort, int serviceId, PacketRegistry registry) {
        this(hubHost, hubPort, serviceId, registry, "");
    }

    /** @param secret shared mesh secret (hub's {@code CONTROL_SECRET}); blank if the mesh has none configured. */
    protected ServiceClient(String hubHost, int hubPort, int serviceId, PacketRegistry registry, String secret) {
        super(hubHost, hubPort, registry);
        this.serviceId = serviceId;
        this.registry = registry;
        this.secret = secret != null ? secret : "";
        onConnect((channel, ctx) -> identify(ctx));
    }

    public int serviceId() { return serviceId; }

    // ── identify ──────────────────────────────────────────────────────────

    private void identify(ChannelHandlerContext ctx) {
        ServiceIdentify request = new ServiceIdentify(new ServiceIdentify.Handshake(serviceId, ServiceState.REQUEST, secret));
        transactions.sendTransaction(ctx, request, DEFAULT_TIMEOUT_MS).whenComplete((handshake, error) -> {
            if (error != null) {
                logger.profile("service").warn("identify failed (will reconnect): " + error.getMessage());
                ctx.close();
                return;
            }
            if (handshake == null || handshake.state() != ServiceState.ACCEPT) {
                logger.profile("service").warn("identify rejected by hub — closing");
                ctx.close();
                return;
            }
            logger.profile("service").info("identified as service " + serviceId);
            startHeartbeat(ctx);
            onIdentified();
        });
    }

    /** Hook fired once the hub accepts this service's identify (re-fires on each reconnect). */
    protected void onIdentified() {}

    // ── heartbeat ─────────────────────────────────────────────────────────

    /** Periodic liveness probe to the hub; a stale connection (no pong within {@link #PONG_TIMEOUT_MS}) is force-closed to trigger reconnect. */
    private void startHeartbeat(ChannelHandlerContext ctx) {
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        lastPongAt.set(System.currentTimeMillis());
        heartbeatTask = heartbeat.scheduleAtFixedRate(() -> {
            if (!ctx.channel().isActive()) return;
            if (System.currentTimeMillis() - lastPongAt.get() > PONG_TIMEOUT_MS) {
                logger.profile("service").warn("no pong from hub within " + PONG_TIMEOUT_MS + "ms — reconnecting");
                ctx.close();
                return;
            }
            sendPacket(ServiceHeartbeat.ping());
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ── inbound ───────────────────────────────────────────────────────────

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof ServiceIdentify identify && identify.hasResponse()) {
            transactions.completeResponse(identify, ctx);
            return;
        }
        // The client only ever receives pongs here (the hub never initiates a heartbeat) — same reasoning
        // as HubServer's side: direction is implied by who received it, not encoded on the wire.
        if (packet instanceof ServiceHeartbeat) {
            lastPongAt.set(System.currentTimeMillis());
            return;
        }
        if (packet instanceof ServiceMessage message) {
            if (message.hasResponse()) {
                transactions.completeResponse(message, ctx);
                return;
            }
            ServiceEnvelope envelope = message.getRequest();
            if (envelope == null) return;
            Packet inner = envelope.decode(registry);
            if (inner instanceof BatchPacket bp && message.isFireAndForget()) {
                // Coalesced fire-and-forget burst (see sendFireAndForgetBatch) — unwrap and dispatch each
                // sub-packet individually. Only valid for one-way sends: a batched RPC has nowhere to
                // route N replies, so this path is not offered for the request/response case.
                for (Packet sub : bp.decode((id, ts, data) -> {
                    try {
                        return registry.constructPacket(id, ts, ((dev.sweety.netty.packet.buffer.PacketBuffer) data).getBytes());
                    } catch (ReflectiveOperationException e) {
                        logger.profile("service").warn("failed to decode batched sub-packet id " + id + ": " + e.getMessage());
                        return null;
                    }
                })) {
                    if (sub != null) handle(envelope.senderId(), sub);
                }
                return;
            }
            Packet reply = handle(envelope.senderId(), inner);
            if (message.isFireAndForget()) return;   // one-way: never ack
            // RPC request: always answer so the caller's future completes (null reply → empty ack).
            ServiceEnvelope response = ServiceEnvelope.wrap(serviceId, envelope.senderId(), reply, registry);
            sendPacket(new ServiceMessage(message.getRequestId(), response));
        }
    }

    /**
     * Handle an inbound message from {@code senderId}. Return a reply packet for an RPC response, or
     * {@code null} for none (an empty ack is still sent for RPC requests; nothing for fire-and-forget).
     */
    protected abstract Packet handle(int senderId, Packet request);

    // ── outbound ──────────────────────────────────────────────────────────

    /** One-way send; safe before connect (queued by the underlying client). */
    public void sendFireAndForget(int receiverId, Packet payload) {
        sendPacket(ServiceMessage.fireAndForget(ServiceEnvelope.wrap(serviceId, receiverId, payload, registry)));
    }

    /**
     * Coalesces {@code payloads} into one {@link BatchPacket} and sends it as a single fire-and-forget
     * envelope — one mesh round-trip's worth of framing instead of one per item, for bursts of stateless,
     * no-response packets (mirrors {@code DatasetUploader}'s use of {@link BatchPacket} on the client
     * transport). Only ever for fire-and-forget: a batch has no way to route N individual RPC replies.
     */
    public void sendFireAndForgetBatch(int receiverId, List<? extends Packet> payloads) {
        if (payloads.isEmpty()) return;
        BatchPacket batch = new BatchPacket(registry::getPacketId, payloads.toArray(new Packet[0]));
        sendFireAndForget(receiverId, batch);
    }

    /** RPC to {@code receiverId}; completes with the decoded reply packet ({@code null} = empty ack). */
    public CompletableFuture<Packet> sendRequest(int receiverId, Packet request) {
        return sendRequest(receiverId, request, DEFAULT_TIMEOUT_MS);
    }

    public CompletableFuture<Packet> sendRequest(int receiverId, Packet request, long timeoutMs) {
        ChannelHandlerContext ctx = channelContext();
        if (ctx == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("service " + serviceId + " not connected"));
        }
        ServiceMessage message = new ServiceMessage(ServiceEnvelope.wrap(serviceId, receiverId, request, registry));
        return transactions.sendTransaction(ctx, message, timeoutMs).thenApply(envelope -> {
            if (envelope.isNoRoute()) {
                throw new CompletionException(
                        new IOException("no route to service " + receiverId + " (not connected to hub)"));
            }
            return envelope.decode(registry);
        });
    }

    // ── lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        super.quit(ctx, promise);
        transactions.failAll(new CancellationException("service " + serviceId + " disconnected"));
    }

    @Override
    public void stop() {
        heartbeat.shutdown();
        super.stop();
    }
}
