package dev.sweety.versioning.server.client;

import dev.sweety.util.signature.Watermark;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Version;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {

    private final ConcurrentHashMap<UUID, ClientProfile> profiles = new ConcurrentHashMap<>();

    public ClientProfile getOrCreate(UUID clientId) {
        Instant now = Instant.now();
        return profiles.compute(clientId, (k, existing) -> {
            if (existing == null) {
                //todo channel
                return new ClientProfile(clientId, "stable", now, now);
            }
            return new ClientProfile(existing.clientId(), existing.channel(), existing.firstSeen(), now);
        });
    }

    public Map<String, Object> buildPatchFields(UUID clientId, Version version) {
        ClientProfile profile = getOrCreate(clientId);
        long ts = Instant.now().toEpochMilli();
        return Map.of(
                "CLIENT_ID", profile.clientId(),
                "VERSION", version,
                "TIMESTAMP", Long.toString(ts),
                "BUILD_ID", profile.clientId() + "-" + version + "-" + ts
        );
    }

    public List<Watermark> buildClientWatermarks(UUID clientId, Version version) {
        ClientProfile profile = getOrCreate(clientId);
        return List.of(
                new Watermark("client.channel", profile.channel().getBytes(StandardCharsets.UTF_8)),
                new Watermark("client.id", Utils.toBytes(profile.clientId())),
                new Watermark("client.version", Utils.toBytes(version))
        );
    }

}