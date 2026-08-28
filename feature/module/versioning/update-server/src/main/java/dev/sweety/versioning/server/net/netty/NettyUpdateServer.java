package dev.sweety.versioning.server.net.netty;

import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.versioning.protocol.handshake.HandshakeRequest;
import dev.sweety.versioning.protocol.handshake.HandshakeResponse;
import dev.sweety.versioning.protocol.handshake.HandshakeTransaction;
import dev.sweety.versioning.protocol.handshake.ResponseData;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.protocol.integrity.IntegrityRequest;
import dev.sweety.versioning.protocol.integrity.IntegrityResponse;
import dev.sweety.versioning.protocol.integrity.IntegrityTransaction;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.server.store.BroadcastChannelGroup;
import dev.sweety.versioning.server.store.DownloadSessionRegistry;
import dev.sweety.versioning.server.service.PatchManager;
import dev.sweety.versioning.server.data.ForcedUpdate;
import dev.sweety.versioning.server.data.UpdateDecision;
import dev.sweety.versioning.server.data.UpdateResolver;
import dev.sweety.versioning.server.store.DownloadTokenStore;
import dev.sweety.versioning.server.store.ReleaseBroadcaster;
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
    private final DownloadSessionRegistry downloadSessions;

    public NettyUpdateServer(String host, int port, PacketRegistry packetRegistry, DownloadTokenStore downloadTokenStore, ReleaseService releaseManager, PatchManager patchManager, DownloadSessionRegistry downloadSessions, Runnable stop) {
        super(host, port, packetRegistry);
        this.downloadTokenStore = downloadTokenStore;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
        this.downloadSessions = downloadSessions;
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
        if (packet instanceof IntegrityTransaction integrity && integrity.hasRequest()) {
            final IntegrityRequest request = integrity.getRequest();
            final IntegrityResponse response = downloadSessions.snapshot(request.getToken())
                    .map(s -> new IntegrityResponse(true, s.bytesHashed(), s.totalBytes(),
                            s.rollingSha256(), s.complete(), s.hmacHex(), s.ed25519Base64()))
                    .orElseGet(IntegrityResponse::unknown);
            integrity.toResponse(response);
            this.sendPacket(ctx, integrity);
            return;
        }
        if (packet instanceof HandshakeTransaction transaction && transaction.hasRequest()) {
            final HandshakeRequest request = transaction.getRequest();
            final LauncherInfo info = request.getInfo();

            if (!NettyHandshakeTrust.isAcceptable(info)) {
                transaction.toResponse(HandshakeResponse.unavailable());
                this.sendPacket(ctx, transaction);
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

            transaction.toResponse(new HandshakeResponse(state, responseData));
            this.sendPacket(ctx, transaction);
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
