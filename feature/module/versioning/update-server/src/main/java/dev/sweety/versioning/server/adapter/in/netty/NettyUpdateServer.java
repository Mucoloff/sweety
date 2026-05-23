package dev.sweety.versioning.server.adapter.in.netty;

import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.*;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.server.adapter.out.broadcast.BroadcastChannelGroup;
import dev.sweety.versioning.server.application.patch.PatchManager;
import dev.sweety.versioning.server.domain.client.ForcedUpdate;
import dev.sweety.versioning.server.domain.decision.UpdateDecision;
import dev.sweety.versioning.server.domain.decision.UpdateResolver;
import dev.sweety.versioning.server.port.out.DownloadTokenStore;
import dev.sweety.versioning.server.port.out.ReleaseBroadcaster;
import dev.sweety.versioning.version.ReleaseService;
import dev.sweety.versioning.version.LauncherInfo;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NettyUpdateServer extends SimpleServer implements ReleaseBroadcaster {

    private final DownloadTokenStore downloadTokenStore;
    private final ReleaseService releaseManager;
    private final PatchManager patchManager;
    private final Runnable stop;
    private final BroadcastChannelGroup channelGroup;

    public NettyUpdateServer(String host, int port, PacketRegistry packetRegistry, DownloadTokenStore downloadTokenStore, ReleaseService releaseManager, PatchManager patchManager, Runnable stop) {
        super(host, port, packetRegistry);
        this.downloadTokenStore = downloadTokenStore;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
        this.stop = stop;
        this.channelGroup = new BroadcastChannelGroup(this::sendPacket);
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

            if (!NettyHandshakeTrust.isAcceptable(info)) {
                this.sendPacket(ctx, new HandshakeTransaction(transaction.getRequestId(), HandshakeResponse.unavailable()));
                return;
            }

            final Map<Artifact, Version> versions = info.versions();
            final UUID clientId = info.clientId();
            final Channel channel = info.channel();

            channelGroup.register(ctx, clientId, channel);

            final Map<Artifact, ResponseData> responseData = new HashMap<>();
            State state = State.UP_TO_DATE;

            for (Map.Entry<Artifact, Version> entry : versions.entrySet()) {
                Artifact artifact = entry.getKey();
                Version current = entry.getValue();

                ReleaseInfo latest = releaseManager.resolveLatest(artifact, channel);
                ForcedUpdate forcedUpdate = channelGroup.getForcedUpdate(artifact, clientId);

                UpdateDecision decision = UpdateResolver.resolve(
                        clientId, channel, artifact, current, latest,
                        latest.rollout(), forcedUpdate, patchManager, releaseManager
                );

                if (decision.update()) {
                    String token = downloadTokenStore.generate(
                            clientId, artifact, latest.channel(),
                            decision.targetVersion(), current, decision.downloadType()
                    );
                    state = State.UPDATED;
                    responseData.put(artifact, new ResponseData(token, decision.targetVersion(), decision.downloadType()));

                    if (decision.forced()) {
                        channelGroup.removeForcedUpdate(artifact, clientId);
                    }
                }
            }

            this.sendPacket(ctx, new HandshakeTransaction(transaction.getRequestId(), new HandshakeResponse(state, responseData)));
        }
    }

    @Override
    public void broadcast(Artifact artifact, ReleaseInfo target, Channel channel, ReleaseBroadcastType type, @Nullable ReleaseInfo previous) {
        channelGroup.broadcast(artifact, target, channel, type, previous);
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        super.quit(ctx, promise);
        channelGroup.remove(ctx);
    }
}
