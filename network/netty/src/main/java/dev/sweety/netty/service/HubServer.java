package dev.sweety.netty.service;

import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPromise;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central relay of the service mesh: every {@link ServiceClient} dials in and self-identifies with a
 * numeric service id, which the hub maps to its channel. A {@link ServiceMessage} is routed purely on
 * its envelope's {@code receiverId} — the inner payload is never decoded here. Single node per id (no
 * pool/replicas): a re-identify simply overwrites the mapping. This class is transport only; subclasses
 * (or callers) supply the registry (must include {@link ServicePackets}) and an optional secret gate.
 */
public class HubServer extends SimpleServer {

    /** serviceId → live channel. */
    private final Map<Integer, ChannelHandlerContext> byId = new ConcurrentHashMap<>();
    /** channel → serviceId, so a disconnect can drop the id mapping. */
    private final Map<ChannelId, Integer> ctxToId = new ConcurrentHashMap<>();
    private final HubRateGate rateGate = new HubRateGate();
    private final String sharedSecret;

    public HubServer(String host, int port, PacketRegistry registry) {
        this(host, port, registry, "");
    }

    /**
     * @param sharedSecret required on every identify when non-blank (mesh {@code CONTROL_SECRET}) — a
     *                      blank secret disables the check, matching every edge's own "empty disables"
     *                      convention. Without it, any process that can reach the hub's port is accepted.
     */
    public HubServer(String host, int port, PacketRegistry registry, String sharedSecret) {
        super(host, port, registry);
        this.sharedSecret = sharedSecret != null ? sharedSecret : "";
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof ServiceIdentify identify && identify.hasRequest()) {
            onIdentify(ctx, identify);
            return;
        }
        if (packet instanceof ServiceMessage message) {
            route(ctx, message);
            return;
        }
        // The hub only ever receives pings here (edges never send it a pong) — direction is implied by
        // which side received the packet, not by any content, so no flag on the wire to check.
        if (packet instanceof ServiceHeartbeat) {
            sendPacket(ctx, ServiceHeartbeat.pong());
        }
    }

    /** Register (or re-register) the channel under its declared service id and accept. */
    private void onIdentify(ChannelHandlerContext ctx, ServiceIdentify identify) {
        int serviceId = identify.getRequest().serviceId();
        if (!sharedSecret.isEmpty() && !constantTimeEquals(sharedSecret, identify.getRequest().secret())) {
            logger.profile("hub").warn("service " + serviceId + " identify rejected — bad secret ("
                    + ctx.channel().remoteAddress() + ")");
            sendPacket(ctx, new ServiceIdentify(identify.getRequestId(),
                    new ServiceIdentify.Handshake(serviceId, ServiceState.REJECT)));
            ctx.channel().close();
            return;
        }
        if (!acceptIdentify(ctx, serviceId)) {
            sendPacket(ctx, new ServiceIdentify(identify.getRequestId(),
                    new ServiceIdentify.Handshake(serviceId, ServiceState.REJECT)));
            ctx.channel().close();
            return;
        }
        byId.put(serviceId, ctx);
        ctxToId.put(ctx.channel().id(), serviceId);
        logger.profile("hub").info("service " + serviceId + " identified (" + ctx.channel().remoteAddress() + ")");
        sendPacket(ctx, new ServiceIdentify(identify.getRequestId(),
                new ServiceIdentify.Handshake(serviceId, ServiceState.ACCEPT)));
    }

    /** Override to gate identify further (e.g. id whitelist) — the shared-secret check runs first. */
    protected boolean acceptIdentify(ChannelHandlerContext ctx, int serviceId) {
        return true;
    }

    /**
     * Forward the message to the target service's channel. If it's not connected: an RPC request gets an
     * immediate no-route nack bounced straight back to the sender (so its {@code sendRequest} future fails
     * in ms, not after riding out the full timeout); a fire-and-forget or an already-in-flight response
     * leg has nowhere useful to bounce to, so those are just dropped (logged).
     *
     * <p>The envelope's {@code senderId} is overwritten with the identity this exact channel actually
     * proved at identify time ({@link #ctxToId}), never trusted from the wire payload itself — otherwise
     * any connected participant could forge {@code senderId} to impersonate a higher-trust service (e.g.
     * SESSION) and reach a callee that trusts its declared caller instead of re-checking it. An
     * unidentified channel (spoke before/without a successful identify) has no verified id — its message
     * is dropped rather than forwarded with an unverifiable sender.
     */
    private void route(ChannelHandlerContext ctx, ServiceMessage message) {
        ServiceEnvelope envelope = message.hasRequest() ? message.getRequest() : message.getResponse();
        if (envelope == null) return;
        Integer verifiedSenderId = ctxToId.get(ctx.channel().id());
        if (verifiedSenderId == null) {
            logger.profile("hub").warn("dropping service message from an unidentified channel ("
                    + ctx.channel().remoteAddress() + ")");
            return;
        }
        envelope.overrideSenderId(verifiedSenderId);
        if (rateGate.exceeded(envelope.senderId())) {
            logger.profile("hub").warn("service " + envelope.senderId() + " exceeded rate cap — dropping "
                    + message.requestCode());
            return;
        }
        ChannelHandlerContext target = byId.get(envelope.receiverId());
        if (target != null && target.channel().isActive()) {
            sendPacket(target, message);
            return;
        }
        logger.profile("hub").warn("no route to service " + envelope.receiverId()
                + " (from " + envelope.senderId() + ") — " + message.requestCode());
        if (!message.hasRequest() || message.getRequestId() == ServiceMessage.FIRE_AND_FORGET_ID) return;
        ChannelHandlerContext sender = byId.get(envelope.senderId());
        if (sender == null || !sender.channel().isActive()) return;
        sendPacket(sender, new ServiceMessage(message.getRequestId(),
                ServiceEnvelope.noRoute(envelope.receiverId(), envelope.senderId())));
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        Integer serviceId = ctxToId.remove(ctx.channel().id());
        if (serviceId != null) {
            // Only drop the mapping if this exact channel still owns it (guards a race with re-identify).
            byId.remove(serviceId, ctx);
            logger.profile("hub").info("service " + serviceId + " disconnected");
        }
        super.quit(ctx, promise);
    }

    /** Snapshot of currently-connected service ids. */
    public Set<Integer> connectedServices() {
        return Set.copyOf(byId.keySet());
    }

    /**
     * Constant-time secret compare — String.equals short-circuits on the first mismatched char and
     * leaks timing. Compares SHA-256 digests (fixed 32 bytes) rather than the raw encoded secrets
     * directly, so the byte comparison never branches on the original string's length either.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] da = sha256(a);
        byte[] db = sha256(b);
        // Both are fixed 32-byte SHA-256 digests regardless of the original secret's length, so this
        // loop never branches on (and can't leak) anything about the original strings.
        int r = 0;
        for (int i = 0; i < da.length; i++) r |= da[i] ^ db[i];
        return r == 0;
    }

    private static byte[] sha256(String s) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
