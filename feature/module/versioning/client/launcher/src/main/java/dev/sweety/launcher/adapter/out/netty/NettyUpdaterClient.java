package dev.sweety.launcher.adapter.out.netty;

import dev.sweety.launcher.domain.update.UpdatePlan;
import dev.sweety.launcher.infra.LauncherConfig;
import dev.sweety.launcher.port.in.ApplyUpdateUseCase;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.versioning.protocol.handshake.*;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Netty-based adapter that connects to the update server and delegates
 * responses to {@link ApplyUpdateUseCase}.
 */
public class NettyUpdaterClient extends SimpleClient {

    private final ApplyUpdateUseCase applyUpdate;
    private final Runnable stop;
    private final AtomicReference<LauncherConfig> config;

    private final BiConsumer<ChannelHandlerContext, LauncherInfo> requestDownload = (ctx, info) ->
            sendPacket(ctx, new HandshakeTransaction(new HandshakeRequest(info)));

    public NettyUpdaterClient(AtomicReference<LauncherConfig> config, IPacketRegistry packetRegistry, ApplyUpdateUseCase applyUpdate, Runnable stop) {
        this(config, packetRegistry, -1, applyUpdate, stop);
    }

    public NettyUpdaterClient(AtomicReference<LauncherConfig> config, IPacketRegistry packetRegistry, int localPort, ApplyUpdateUseCase applyUpdate, Runnable stop) {
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

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
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
                System.out.println("forced update detected!");
                System.out.println("Current version: " + cfg.versions().get(artifact) + " " + cfg.channel());
                System.out.println("Target update version: " + info);
                System.out.println("Broadcast type: " + releasePacket.type());
            }

            this.requestDownload.accept(ctx, cfg.info());
        }
    }
}
