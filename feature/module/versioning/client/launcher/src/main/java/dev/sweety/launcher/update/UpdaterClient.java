package dev.sweety.launcher.update;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.version.LatestInfo;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.protocol.handshake.*;
import dev.sweety.versioning.version.Version;
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

    private final BiConsumer<ChannelHandlerContext, LauncherInfo> requestDownload = (ctx, info) ->
            sendPacket(ctx, new HandshakeTransaction(new HandshakeRequest(info)));

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
            final Optional<Version> appVersion = response.getAppVersion();
            final Optional<String> launcherToken = response.getLauncherToken();
            final Optional<Version> launcherVersion = response.getLauncherVersion();

            switch (state) {
                case APP -> {
                    appToken.ifPresent(updateManager::downloadAppUpdate);
                    appVersion.ifPresent(version -> config.getAndUpdate(conf -> conf.withApp(version)));
                }
                case LAUNCHER -> {
                    launcherToken.ifPresent(updateManager::downloadLauncherUpdate);
                    launcherVersion.ifPresent(version -> config.getAndUpdate(conf -> conf.withLauncher(version)));
                }
                case BOTH -> {
                    appToken.ifPresent(updateManager::downloadAppUpdate);
                    launcherToken.ifPresent(updateManager::downloadLauncherUpdate);

                    this.config.getAndUpdate(config -> {
                        if (appVersion.isPresent() && launcherVersion.isPresent())
                            return config.withVersions(launcherVersion.get(), appVersion.get());
                        return appVersion.map(config::withApp).orElseGet(() -> launcherVersion.map(config::withLauncher).orElse(config));
                    });
                }
                case UP_TO_DATE -> updateManager.upToDate();
                case UNAVAILABLE -> updateManager.unavailable();
            }
        } else if (packet instanceof ReleasePacket releasePacket) {
            final LatestInfo state = releasePacket.state();
            final LauncherConfig config = this.config.get();

            if (releasePacket.forced()) {
                System.out.println("forced rollback detected!");
                System.out.println("Current versions: " + config.info());
                System.out.println("Target rollback versions: " + state);
            } else {
                boolean updateApp = state.app().newerThan(config.app());
                boolean updateLauncher = state.launcher().newerThan(config.launcher());
                if (!updateApp && !updateLauncher) return;
            }

            this.requestDownload.accept(ctx, config.info());
        }
    }


}
