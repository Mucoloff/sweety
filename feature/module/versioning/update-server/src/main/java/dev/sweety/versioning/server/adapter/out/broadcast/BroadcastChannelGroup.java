package dev.sweety.versioning.server.adapter.out.broadcast;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.time.store.ExpiryCache;
import dev.sweety.time.store.ExpiryStore;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.protocol.update.ReleasePacket;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.domain.client.ClientInfo;
import dev.sweety.versioning.server.domain.client.ForcedUpdate;
import dev.sweety.versioning.server.port.out.ReleaseBroadcaster;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class BroadcastChannelGroup implements ReleaseBroadcaster {

    private final ConcurrentHashMap<ChannelHandlerContext, ClientInfo> clients = new ConcurrentHashMap<>();
    private final Map<Artifact, ExpiryCache<UUID, ForcedUpdate>> forcedUpdates = new ConcurrentHashMap<>();
    private final BiConsumer<ChannelHandlerContext, Packet> sender;

    public BroadcastChannelGroup(BiConsumer<ChannelHandlerContext, Packet> sender) {
        this.sender = sender;
    }

    public void register(ChannelHandlerContext ctx, UUID clientId, Channel channel) {
        clients.put(ctx, new ClientInfo(clientId, channel));
    }

    public void remove(ChannelHandlerContext ctx) {
        ClientInfo client = clients.remove(ctx);
        if (client != null) {
            forcedUpdates.forEach((artifact, garbage) -> garbage.remove(client.id()));
        }
    }

    public @Nullable ForcedUpdate getForcedUpdate(Artifact artifact, UUID clientId) {
        ExpiryCache<UUID, ForcedUpdate> cache = forcedUpdates.get(artifact);
        return cache != null ? cache.get(clientId) : null;
    }

    public void removeForcedUpdate(Artifact artifact, UUID clientId) {
        ExpiryCache<UUID, ForcedUpdate> cache = forcedUpdates.get(artifact);
        if (cache != null) cache.remove(clientId);
    }

    @Override
    public void broadcast(Artifact artifact, ReleaseInfo target, Channel channel, ReleaseBroadcastType type, @Nullable ReleaseInfo previous) {
        boolean isForced = type == ReleaseBroadcastType.FORCED || type == ReleaseBroadcastType.ROLLBACK;
        ReleasePacket packet = new ReleasePacket(artifact, target, type);

        ForcedUpdate forcedUpdate = null;
        if (isForced) {
            forcedUpdate = new ForcedUpdate(
                    channel,
                    previous != null ? previous.version() : null,
                    target.version(),
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Settings.DOWNLOAD_EXPIRE_DELAY_MS)
            );
        }

        ExpiryCache<UUID, ForcedUpdate> garbage = forcedUpdates.computeIfAbsent(
                artifact, a -> ExpiryStore.of(Settings.MAX_CONCURRENT_DOWNLOADS));

        ForcedUpdate finalForcedUpdate = forcedUpdate;
        clients.entrySet().stream()
                .filter(e -> e.getValue().channel().accepts(channel))
                .forEach(e -> {
                    UUID clientId = e.getValue().id();
                    if (finalForcedUpdate != null) garbage.add(clientId, finalForcedUpdate);
                    sender.accept(e.getKey(), packet);
                });
    }
}
