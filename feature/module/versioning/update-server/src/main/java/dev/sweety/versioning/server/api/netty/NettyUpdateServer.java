package dev.sweety.versioning.server.api.netty;

import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;
import dev.sweety.versioning.protocol.handshake.*;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.logic.decision.UpdateDecision;
import dev.sweety.versioning.server.logic.decision.UpdateResolver;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.util.garbage.ExpirableGarbage;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.server.logic.download.DownloadManager;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NettyUpdateServer extends SimpleServer {

    private final DownloadManager downloadManager;
    private final ReleaseManager releaseManager;
    private final Runnable stop;

    private final PatchManager patchManager;

    private final EnumMap<Artifact, ExpirableGarbage<UUID, ForcedUpdate>> forcedUpdates = new EnumMap<>(Artifact.class);

    private final ConcurrentHashMap<ChannelHandlerContext, ClientInfo> clientInfos = new ConcurrentHashMap<>();

    public NettyUpdateServer(String host, int port, IPacketRegistry packetRegistry, DownloadManager downloadManager, ReleaseManager releaseManager, PatchManager patchManager, Runnable stop) {
        super(host, port, packetRegistry);
        this.downloadManager = downloadManager;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
        this.stop = stop;

        for (Artifact value : Artifact.values())
            forcedUpdates.put(value, new ExpirableGarbage<>(Settings.MAX_CONCURRENT_DOWNLOADS));
    }

    @Override
    public void stop() {
        super.stop();
        this.stop.run();
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof HandshakeTransaction transaction && transaction.hasRequest()) {
            final HandshakeRequest request = transaction.getRequest();
            final LauncherInfo info = request.getInfo();

            final EnumMap<Artifact, Version> versions = info.versions();

            //todo client trust based !!!
            final UUID buildId = info.buildId();
            final UUID clientId = info.clientId();
            final Channel channel = info.channel();

            this.clientInfos.put(ctx, new ClientInfo(clientId, channel));

            final EnumMap<Artifact, ResponseData> responseData = new EnumMap<>(Artifact.class);

            State state = State.UP_TO_DATE;

            for (Artifact artifact : Artifact.values()) {

                ReleaseInfo latest = releaseManager.resolveLatest(artifact, channel);

                final Version current = versions.get(artifact);

                ForcedUpdate forcedUpdate = null;

                var garbage = forcedUpdates.get(artifact);
                try {
                    forcedUpdate = garbage.get(clientId);
                } catch (InvalidTokenException | TokenExpiredException ignored) {}

                UpdateDecision decision = UpdateResolver.resolve(
                        clientId,
                        channel,
                        artifact,
                        current,
                        latest,
                        latest.rollout(),
                        forcedUpdate,
                        patchManager,
                        releaseManager
                );

                if (decision.update()) {
                    String token = downloadManager.generate(
                            clientId,
                            artifact,
                            latest.channel(),
                            decision.targetVersion(),
                            current,
                            decision.downloadType()
                    );

                    state = State.UPDATED;
                    responseData.put(artifact, new ResponseData(token, decision.targetVersion(), decision.downloadType()));

                    if (decision.forced()) {
                        garbage.remove(clientId);
                    }
                }
            }

            final HandshakeResponse response = new HandshakeResponse(state, responseData);

            this.sendPacket(ctx, new HandshakeTransaction(transaction.getRequestId(), response));
        }
    }

    public void broadcast(
            Artifact artifact,
            ReleaseInfo target,
            Channel channel,
            ReleaseBroadcastType type,
            @Nullable ReleaseInfo previous
    ) {

        final boolean isForced = type == ReleaseBroadcastType.FORCED || type == ReleaseBroadcastType.ROLLBACK;

        final ReleasePacket packet = new ReleasePacket(artifact, target, type);

        // forced update solo per alcuni casi
        final ForcedUpdate forcedUpdate;

        if (isForced) {
            forcedUpdate = new ForcedUpdate(
                    channel,
                    previous != null ? previous.version() : null,
                    target.version(),
                    System.currentTimeMillis() + Settings.DOWNLOAD_EXPIRE_DELAY_MS
            );
        } else forcedUpdate = null;

        final ExpirableGarbage<UUID, ForcedUpdate> garbage =
                this.forcedUpdates.get(artifact);

        this.clientInfos.entrySet()
                .stream()
                .filter(entry -> entry.getValue().channel().accepts(channel))
                .forEach((entry) -> {
                    ChannelHandlerContext ctx = entry.getKey();
                    UUID clientId = entry.getValue().id();
                    if (forcedUpdate != null) {
                        garbage.add(clientId, forcedUpdate);
                    }

                    sendPacket(ctx, packet);
                });
    }


    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        super.quit(ctx, promise);
        ClientInfo client = this.clientInfos.remove(ctx);
        if (client != null) {
            this.forcedUpdates.forEach((artifact, garbage) -> garbage.remove(client.id()));
        }
    }

}
