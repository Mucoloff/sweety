package dev.sweety.launcher.update;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.version.LatestInfo;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.protocol.handshake.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class UpdaterClient extends SimpleClient {

    private final UpdateManager updateManager;
    private final Runnable stop;
    private final AtomicReference<LauncherConfig> config;

    public UpdaterClient(AtomicReference<LauncherConfig> config, IPacketRegistry packetRegistry, UpdateManager updateManager, Runnable stop) {
        this(config, packetRegistry, -1, updateManager, stop);
    }

    private final BiConsumer<ChannelHandlerContext, LauncherInfo> requestDownload = (ctx, info) -> {
        sendPacket(ctx, new HandshakeTransaction(new HandshakeRequest(info)));
    };

    public UpdaterClient(AtomicReference<LauncherConfig> config, IPacketRegistry packetRegistry, int localPort, UpdateManager updateManager, Runnable stop) {
        super(config.get().nettyHost(), config.get().nettyPort(), packetRegistry, localPort);
        this.config = config;
        this.updateManager = updateManager;
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
            final Optional<String> appToken = response.getAppToken();
            final Optional<String> launcherToken = response.getLauncherToken();

            switch (state) {
                case APP -> appToken.ifPresent(updateManager::downloadAppUpdate);
                case LAUNCHER -> launcherToken.ifPresent(updateManager::downloadLauncherUpdate);
                case BOTH -> {
                    appToken.ifPresent(updateManager::downloadAppUpdate);
                    launcherToken.ifPresent(updateManager::downloadLauncherUpdate);
                }
                case UP_TO_DATE -> updateManager.upToDate();
                case UNAVAILABLE -> updateManager.unavailable();
            }
        } else if (packet instanceof ReleasePacket releasePacket) {
            final LatestInfo state = releasePacket.state();

            final LauncherConfig config = this.config.updateAndGet(old -> {
                boolean updateApp = state.app().newerThan(old.app());
                boolean updateLauncher = state.launcher().newerThan(old.launcher());

                if (!updateApp && !updateLauncher) return old;

                return old.withVersions(
                        updateLauncher ? state.launcher() : old.launcher(),
                        updateApp ? state.app() : old.app()
                );
            });

            this.requestDownload.accept(ctx, config.info());
        }
    }


}
