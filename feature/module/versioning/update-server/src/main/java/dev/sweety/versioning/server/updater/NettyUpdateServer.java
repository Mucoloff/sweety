package dev.sweety.versioning.server.updater;

import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.time.Expirable;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.server.util.Garbage;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.protocol.handshake.HandshakeRequest;
import dev.sweety.versioning.protocol.handshake.HandshakeResponse;
import dev.sweety.versioning.protocol.handshake.HandshakeTransaction;
import dev.sweety.versioning.server.download.DownloadManager;
import dev.sweety.versioning.server.release.ReleaseManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class NettyUpdateServer extends SimpleServer {

    private static final long EXPIRE_DELAY_MS = 30_000L;
    private static final int MAX_GARBAGE = 50;

    private final DownloadManager downloadManager;
    private final ReleaseManager releaseManager;
    private final Runnable stop;

    private final Garbage<ChannelHandlerContext, ForcedUpdate> forcedUpdates = new Garbage<>(MAX_GARBAGE);

    public NettyUpdateServer(String host, int port, IPacketRegistry packetRegistry, DownloadManager downloadManager, ReleaseManager releaseManager, Runnable stop) {
        super(host, port, packetRegistry);
        this.downloadManager = downloadManager;
        this.releaseManager = releaseManager;
        this.stop = stop;
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

            final ReleaseInfo latest = releaseManager.latest();
            final HandshakeResponse response = handleUpdateRequest(isForced(ctx, info), latest, info);

            this.sendPacket(ctx, new HandshakeTransaction(transaction.getRequestId(), response));
        }
    }

    private Force isForced(ChannelHandlerContext ctx, LauncherInfo info) {
        final @NotNull ForcedUpdate update;

        try {
            update = this.forcedUpdates.consume(ctx);
        } catch (TokenExpiredException | InvalidTokenException e) {
            return Force.FALSE;
        }

        final ReleaseInfo rolled = update.rolled();
        final ReleaseInfo prev = update.prev();

        final boolean forceLauncher = !rolled.launcher().equals(prev.launcher()) && !info.launcher().equals(rolled.launcher());
        final boolean forceApp = !rolled.app().equals(prev.app()) && !info.app().equals(rolled.app());

        return Force.of(forceLauncher, forceApp);
    }

    private HandshakeResponse handleUpdateRequest(final Force forced, ReleaseInfo latest, LauncherInfo info) {

        final boolean updateLauncher = latest.launcher().newerThan(info.launcher()) || forced.launcher();
        final boolean updateApp = latest.app().newerThan(info.app()) || forced.app();

        if (updateApp && updateLauncher) {
            String appToken = downloadManager.generate(info.clientId(), Artifact.APP, latest.app());
            String launcherToken = downloadManager.generate(info.clientId(), Artifact.LAUNCHER, latest.launcher());
            return HandshakeResponse.both(appToken, latest.app(), launcherToken, latest.launcher());
        }

        if (updateApp) {
            String appToken = (downloadManager.generate(info.clientId(), Artifact.APP, latest.app()));
            return HandshakeResponse.app(appToken, latest.app());
        }

        if (updateLauncher) {
            String launcherToken = (downloadManager.generate(info.clientId(), Artifact.LAUNCHER, latest.launcher()));
            return HandshakeResponse.launcher(launcherToken, latest.launcher());
        }

        return HandshakeResponse.upToDate();
    }

    public void broadcastRelease(ReleaseInfo state) {
        broadcastPacket(new ReleasePacket(state, false));
    }

    public void broadcastRollback(ReleaseInfo prev, ReleaseInfo rolled) {
        final ReleasePacket packet = new ReleasePacket(rolled, true);
        final ForcedUpdate update = new ForcedUpdate(prev, rolled, System.currentTimeMillis() + EXPIRE_DELAY_MS);
        this.clients().values().forEach((ctx) -> {
            this.forcedUpdates.add(ctx, update);
            sendPacket(ctx, packet);
        });
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        super.quit(ctx, promise);
        this.forcedUpdates.remove(ctx);
    }

    private record ForcedUpdate(ReleaseInfo prev, ReleaseInfo rolled, long expireAt) implements Expirable {
    }

    private record Force(boolean launcher, boolean app) {
        private static final Force FALSE = new Force(false, false);

        private static final Map<Byte, Force> CACHE = Map.of(
                (byte) 0b00, FALSE,
                (byte) 0b10, new Force(true, false),
                (byte) 0b01, new Force(false, true),
                (byte) 0b11, new Force(true, true)
        );

        public static Force of(boolean launcher, boolean app) {
            return CACHE.get((byte) ((launcher ? 0b10 : 0) | (app ? 0b1 : 0)));
        }

    }
}
