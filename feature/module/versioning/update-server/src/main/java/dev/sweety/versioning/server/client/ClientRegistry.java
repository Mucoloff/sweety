package dev.sweety.versioning.server.client;

import dev.sweety.util.signature.Watermark;
import dev.sweety.versioning.server.cache.CacheKey;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {

    private final ConcurrentHashMap<UUID, ClientProfile> profiles = new ConcurrentHashMap<>();

    public ClientProfile getOrCreate(UUID clientId, Channel channel) {
        Instant now = Instant.now();
        return profiles.compute(clientId, (id, existing) -> {
            if (existing == null) return new ClientProfile(id, channel, now, now);
            return new ClientProfile(existing.clientId(), channel, existing.firstSeen(), now);
        });
    }

    public Map<String, Object> buildPatchFields(CacheKey key) {
        UUID clientId = key.clientId();
        Version version = key.version();
        ClientProfile profile = getOrCreate(clientId, key.channel());
        long ts = Instant.now().toEpochMilli();
        return Map.of(
                "CLIENT_ID", profile.clientId().toString(),
                "CHANNEL", profile.channel(),
                "VERSION", version.toString(),
                "TIMESTAMP", Long.toString(ts),
                "BUILD_ID", profile.clientId() + "-" + version + "-" + ts
        );
    }

    public List<Watermark> buildClientWatermarks(CacheKey key) {
        UUID clientId = key.clientId();
        ClientProfile profile = getOrCreate(clientId, key.channel());
        return List.of(
                new Watermark("client.channel", Utils.toBytes(profile.channel().ordinal())),
                new Watermark("client.id", Utils.toBytes(profile.clientId())),
                new Watermark("client.version", Utils.toBytes(key.version()))
        );
    }

}