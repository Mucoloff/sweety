package dev.sweety.versioning.server.logic.client;

import dev.sweety.util.signature.Signature;
import dev.sweety.util.signature.Watermark;
import dev.sweety.versioning.server.logic.cache.CacheKey;
import dev.sweety.versioning.server.logic.patch.PatchDefinition;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
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

    public PatchDefinition createPatchDefinition(CacheKey key) {
        UUID clientId = key.clientId();
        Version version = key.version();
        ClientProfile profile = getOrCreate(clientId, key.channel());
        
        long ts = Instant.now().toEpochMilli();
        UUID buildId = UUID.nameUUIDFromBytes((clientId.toString() + version.toString() + ts).getBytes(StandardCharsets.UTF_8));

        // 1. Fields
        Map<String, Object> fields = Map.of(
                "CLIENT_ID", profile.clientId().toString(),
                "CHANNEL", profile.channel().prettyName(),
                "VERSION", version.toString(),
                "TIMESTAMP", Long.toString(ts),
                "BUILD_ID", buildId.toString()
        );

        // 2. Watermarks
        List<Watermark> watermarks = new ArrayList<>();
        // Profile based
        watermarks.add(new Watermark("client.channel", Utils.toBytes(profile.channel().ordinal())));
        watermarks.add(new Watermark("client.id", Utils.toBytes(profile.clientId())));
        watermarks.add(new Watermark("client.version", Utils.toBytes(key.version())));
        
        // Injection based (moved from JarInjector to maintain consistency with 'ts')
        watermarks.add(new Watermark("client", Utils.toBytes(clientId)));
        watermarks.add(new Watermark("version", Utils.toBytes(version)));
        watermarks.add(new Watermark("timestamp", Long.toString(ts).getBytes(StandardCharsets.UTF_8)));

        // 3. Manifest
        Map<String, String> manifest = new HashMap<>();
        manifest.put("Patched", "true");
        manifest.put("Patched-For", clientId.toString());
        manifest.put("Patched-Version", version.toString());
        manifest.put("Patched-At", Long.toString(ts));

        return new PatchDefinition(
                fields,
                watermarks,
                manifest,
                Signature.BUILD_INFO_CLASS
        );
    }
    
}