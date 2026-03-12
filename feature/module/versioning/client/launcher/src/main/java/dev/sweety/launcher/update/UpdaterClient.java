package dev.sweety.launcher.update;

import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.version.LatestInfo;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.protocol.handshake.*;
import io.netty.channel.ChannelHandlerContext;

import java.util.Optional;

public class UpdaterClient extends SimpleClient {

    private final UpdateManager updateManager;
    private final Runnable stop;

    public UpdaterClient(String host, int port, IPacketRegistry packetRegistry, LauncherInfo info, UpdateManager updateManager, Runnable stop) {
        this(host, port, packetRegistry, -1, info, updateManager, stop);
    }

    public UpdaterClient(String host, int port, IPacketRegistry packetRegistry, int localPort, LauncherInfo info, UpdateManager updateManager, Runnable stop) {
        super(host, port, packetRegistry, localPort);
        this.updateManager = updateManager;
        this.stop = stop;

        onConnect((c, ctx) -> {
            sendPacket(ctx, new HandshakeTransaction(new HandshakeRequest(info)));
        });
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

            //todo
        }
    }


}
