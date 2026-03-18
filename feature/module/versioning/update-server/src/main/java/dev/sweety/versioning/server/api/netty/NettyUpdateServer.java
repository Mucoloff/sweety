package dev.sweety.versioning.server.api.netty;

import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.time.Expirable;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;
import dev.sweety.versioning.protocol.handshake.*;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.server.Settings;
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

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NettyUpdateServer extends SimpleServer {

    private static final long EXPIRE_DELAY_MS = 30_000L;
    private static final int MAX_GARBAGE = 50;

    private final DownloadManager downloadManager;
    private final ReleaseManager releaseManager;
    private final Runnable stop;

    private final PatchManager patchManager;

    private final EnumMap<Artifact, ExpirableGarbage<ChannelHandlerContext, ForcedUpdate>> forcedUpdates = new EnumMap<>(Artifact.class);

    private final ConcurrentHashMap<ChannelHandlerContext, ClientInfo> clientInfos = new ConcurrentHashMap<>();

    public NettyUpdateServer(String host, int port, IPacketRegistry packetRegistry, DownloadManager downloadManager, ReleaseManager releaseManager, PatchManager patchManager, Runnable stop) {
        super(host, port, packetRegistry);
        this.downloadManager = downloadManager;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
        this.stop = stop;

        for (Artifact value : Artifact.values()) forcedUpdates.put(value, new ExpirableGarbage<>(MAX_GARBAGE));
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
                final ReleaseInfo latest = releaseManager.latest(artifact, channel);

                final Channel releaseChannel = latest.channel();
                final Version current = versions.get(artifact);

                final Version version = latest.version();

                boolean forced = isForced(ctx, artifact, channel, current);

                final boolean update = version.newerThan(current) || forced;

                if (update) {

                    DownloadType type = chooseType(artifact, releaseChannel, version, current);

                    String token = downloadManager.generate(clientId, artifact, releaseChannel, version, current, type);
                    state = State.UPDATED;
                    responseData.put(artifact, new ResponseData(token, version, type));
                }
            }

            final HandshakeResponse response = new HandshakeResponse(state, responseData);

            this.sendPacket(ctx, new HandshakeTransaction(transaction.getRequestId(), response));
        }
    }

    private @NotNull DownloadType chooseType(Artifact artifact, Channel releaseChannel, Version version, Version current) {
        final long size;
        try {
            size = this.releaseManager.resolveBaseJar(artifact, releaseChannel, version).toFile().length();
        } catch (Exception e) {
            return DownloadType.FULL;
        }

        Optional<File> cachedPatch = this.patchManager.cached(artifact, releaseChannel, version, current);

        if (cachedPatch.isEmpty() || cachedPatch.get().length() >= size * Settings.PERCENT_SIZE)
            return DownloadType.FULL;

        return DownloadType.PATCH;
    }

    private boolean isForced(ChannelHandlerContext ctx, Artifact artifact, Channel channel, Version current) {
        final @NotNull ForcedUpdate update;
        try {
            update = this.forcedUpdates.get(artifact).consume(ctx);
        } catch (TokenExpiredException | InvalidTokenException e) {
            System.out.println("not present in forced");
            return false;
        }

        // user channel < release channel // the user shouldn't be updated
        if (!channel.accepts(update.channel())) {
            System.out.println("user " + channel + " < release" + update.channel() + ": no forced update");
            return false;
        }

        final Version rolled = update.rolled();
        final Version prev = update.prev();

        if (rolled.equals(prev)) {
            System.out.println("same rolled and prev");
            return false;
        }

        if (current.equals(rolled)) {
            System.out.println("same rolled and current");
            return false;
        }
        return true;
    }

    public void broadcastRelease(Artifact artifact, ReleaseInfo state) {
        final Channel channel = state.channel();
        final ReleasePacket packet = new ReleasePacket(artifact, state, false);

        this.clientInfos.entrySet()
                .stream()
                .filter((entry) -> channel.accepts(entry.getValue().channel()))
                .map(Map.Entry::getKey)
                .forEach((ctx) -> sendPacket(ctx, packet));
    }

    public void broadcastRollback(Artifact artifact, Channel channel, ReleaseInfo rolled, ReleaseInfo prev) {
        //rolled = new
        final ReleasePacket packet = new ReleasePacket(artifact, rolled, true);
        final ForcedUpdate update = new ForcedUpdate(channel, prev.version(), rolled.version(), System.currentTimeMillis() + EXPIRE_DELAY_MS);
        final ExpirableGarbage<ChannelHandlerContext, ForcedUpdate> garbage = this.forcedUpdates.get(artifact);

        this.clientInfos.entrySet()
                .stream()
                .filter((entry) -> channel.accepts(entry.getValue().channel()))
                .map(Map.Entry::getKey)
                .forEach((ctx) -> {
                    garbage.add(ctx, update);
                    sendPacket(ctx, packet);
                });
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        super.quit(ctx, promise);
        this.forcedUpdates.forEach((artifact, garbage) -> garbage.remove(ctx));
        this.clientInfos.remove(ctx);
    }


    private record ForcedUpdate(Channel channel, Version prev, Version rolled,
                                long expireAt) implements Expirable {
    }

    private record ClientInfo(UUID id, Channel channel) {

    }

}
