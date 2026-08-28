package dev.sweety.launcher.net;

import dev.sweety.launcher.data.UpdatePlan;
import dev.sweety.launcher.LauncherConfig;
import dev.sweety.launcher.service.ApplyUpdateUseCase;
import dev.sweety.launcher.service.IntegrityProbePort;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.HandshakeRequest;
import dev.sweety.versioning.protocol.handshake.HandshakeResponse;
import dev.sweety.versioning.protocol.handshake.HandshakeTransaction;
import dev.sweety.versioning.protocol.handshake.ResponseData;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.protocol.integrity.IntegrityRequest;
import dev.sweety.versioning.protocol.integrity.IntegrityResponse;
import dev.sweety.versioning.protocol.integrity.IntegrityTransaction;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Netty-based adapter that connects to the update server and delegates
 * responses to {@link ApplyUpdateUseCase}.
 */
public class NettyUpdaterClient extends SimpleClient implements IntegrityProbePort {

    private final ApplyUpdateUseCase applyUpdate;
    private final Runnable stop;
    private final AtomicReference<LauncherConfig> config;

    /** Pending integrity transactions keyed by requestId, completed when the matching response arrives. */
    private final ConcurrentHashMap<Long, CompletableFuture<IntegrityResponse>> pendingIntegrity = new ConcurrentHashMap<>();

    private final BiConsumer<ChannelHandlerContext, LauncherInfo> requestDownload = (ctx, info) ->
            sendPacket(ctx, new HandshakeTransaction(new HandshakeRequest(info)));

    public NettyUpdaterClient(AtomicReference<LauncherConfig> config, PacketRegistry packetRegistry, ApplyUpdateUseCase applyUpdate, Runnable stop) {
        this(config, packetRegistry, -1, applyUpdate, stop);
    }

    public NettyUpdaterClient(AtomicReference<LauncherConfig> config, PacketRegistry packetRegistry, int localPort, ApplyUpdateUseCase applyUpdate, Runnable stop) {
        super(config.get().host(), config.get().port(), packetRegistry, localPort);
        this.config = config;
        this.applyUpdate = applyUpdate;
        this.stop = stop;

        final LauncherInfo info = config.get().info();
        onConnect((c, ctx) -> this.requestDownload.accept(ctx, info));
    }

    @Override
    public void stop() {
        super.stop();
        this.stop.run();
    }

    /** Sends an {@link IntegrityRequest} for {@code token} and completes when the server responds. */
    @Override
    public CompletableFuture<IntegrityResponse> probe(String token) {
        final IntegrityTransaction tx = new IntegrityTransaction(new IntegrityRequest(token));
        final CompletableFuture<IntegrityResponse> future = new CompletableFuture<>();
        pendingIntegrity.put(tx.getRequestId(), future);
        sendPacket(tx).whenComplete((v, t) -> {
            if (t != null) {
                pendingIntegrity.remove(tx.getRequestId());
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof IntegrityTransaction integrity && integrity.hasResponse()) {
            final CompletableFuture<IntegrityResponse> future = pendingIntegrity.remove(integrity.getRequestId());
            if (future != null) future.complete(integrity.getResponse());
            return;
        }
        if (packet instanceof HandshakeTransaction transaction && transaction.hasResponse()) {
            final HandshakeResponse response = transaction.getResponse();
            final State state = response.getState();
            Map<Artifact, ResponseData> versions = response.getVersions();

            switch (state) {
                case UPDATED -> {
                    for (Map.Entry<Artifact, ResponseData> entry : versions.entrySet()) {
                        Artifact artifact = entry.getKey();
                        ResponseData data = entry.getValue();
                        UpdatePlan plan = new UpdatePlan(
                                artifact,
                                config.get().versions().getOrDefault(artifact, dev.sweety.versioning.version.Version.ZERO),
                                data.version(),
                                data.token(),
                                data.type());
                        applyUpdate.applyUpdate(plan);
                        config.getAndUpdate(conf -> conf.with(artifact, data.version()));
                    }
                }
                case UP_TO_DATE -> applyUpdate.markUpToDate();
                case UNAVAILABLE -> applyUpdate.markUnavailable();
            }
        } else if (packet instanceof ReleasePacket releasePacket) {
            final ReleaseInfo info = releasePacket.info();
            final LauncherConfig cfg = this.config.get();
            Artifact artifact = releasePacket.artifact();

            if (releasePacket.type() != ReleaseBroadcastType.NORMAL) {
                logger.profile("release").info("forced update detected! Current version: "
                        + cfg.versions().get(artifact) + " " + cfg.channel() + ", target: " + info
                        + ", broadcast type: " + releasePacket.type());
            }

            this.requestDownload.accept(ctx, cfg.info());
        }
    }
}
