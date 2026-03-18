package dev.sweety.launcher.update;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.netty.messaging.impl.SimpleClient;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.protocol.handshake.*;
import io.netty.channel.ChannelHandlerContext;

import java.util.EnumMap;
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
        super(config.get().host(), config.get().port(), packetRegistry, localPort);
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
            EnumMap<Artifact, ResponseData> versions = response.getVersions();

            switch (state) {
                case UPDATED -> {
                    for (Artifact artifact : Artifact.values()) {
                        ResponseData data = versions.get(artifact);
                        if (data == null) continue;
                        updateManager.downloadUpdate(artifact, data.token(), data.version(), data.type());
                        config.getAndUpdate(conf -> conf.with(artifact, data.version()));
                    }
                }
                case UP_TO_DATE -> updateManager.upToDate();
                case UNAVAILABLE -> updateManager.unavailable();
            }
        } else if (packet instanceof ReleasePacket releasePacket) {
            final ReleaseInfo info = releasePacket.info();
            final LauncherConfig config = this.config.get();

            Artifact artifact = releasePacket.artifact();

            if (releasePacket.forced()) {
                System.out.println("forced rollback detected!");
                System.out.println("Current version: " + config.versions().get(artifact) + " " + config.channel());
                System.out.println("Target rollback version: " + info);


            }

            this.requestDownload.accept(ctx, config.info());
        }
    }


}
