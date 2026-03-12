package dev.sweety.versioning.server.updater;

import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.LatestInfo;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.protocol.handshake.HandshakeRequest;
import dev.sweety.versioning.protocol.handshake.HandshakeResponse;
import dev.sweety.versioning.protocol.handshake.HandshakeTransaction;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.server.download.DownloadManager;
import dev.sweety.versioning.server.release.ReleaseManager;
import io.netty.channel.ChannelHandlerContext;

import java.util.Optional;

public class NettyUpdateServer extends SimpleServer {

    private final DownloadManager downloadManager;
    private final ReleaseManager releaseManager;
    private final Runnable stop;

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

            HandshakeResponse response = handleUpdateRequest(info);

            this.sendPacket(ctx, new HandshakeTransaction(transaction.getRequestId(), response));
        } else {
            //todo
        }
    }

    private HandshakeResponse handleUpdateRequest(LauncherInfo info) {
        final LatestInfo latest = releaseManager.latest();

        final boolean updateLauncher = latest.launcher().newerThan(info.launcher());
        final boolean updateApp = latest.app().newerThan(info.app());

        final HandshakeResponse response;
        if (updateApp && updateLauncher) {
            String appToken = downloadManager.generate(info.clientId(), Artifact.APP, latest.app());
            String launcherToken = downloadManager.generate(info.clientId(), Artifact.LAUNCHER, latest.launcher());
            response = HandshakeResponse.both(appToken, launcherToken);
        } else if (updateApp) {
            String appToken = (downloadManager.generate(info.clientId(), Artifact.APP, latest.app()));
            response = HandshakeResponse.app(appToken);
        } else if (updateLauncher) {
            String launcherToken = (downloadManager.generate(info.clientId(), Artifact.LAUNCHER, latest.launcher()));
            response = HandshakeResponse.launcher(launcherToken);
        } else response = HandshakeResponse.upToDate();

        return response;
    }

    public void broadcastRelease(LatestInfo state) {
        broadcastPacket(new ReleasePacket(state));
    }
}
