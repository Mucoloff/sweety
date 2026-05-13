package dev.sweety.versioning.server.logic.artifact;

import dev.sweety.versioning.version.artifact.Artifact;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArtifactRegistry {

    private final Map<Artifact, ArtifactMetadata> registry = new ConcurrentHashMap<>();
    private final String globalSecret;

    public ArtifactRegistry(String globalSecret) {
        this.globalSecret = globalSecret;
        // Register core artifacts with global secret by default
        register(new ArtifactMetadata(Artifact.APP, globalSecret));
        register(new ArtifactMetadata(Artifact.LAUNCHER, globalSecret));
    }

    public void register(ArtifactMetadata metadata) {
        registry.put(metadata.artifact(), metadata);
    }

    @Nullable
    public ArtifactMetadata getMetadata(Artifact artifact) {
        return registry.get(artifact);
    }

    public String getSecret(Artifact artifact) {
        ArtifactMetadata metadata = getMetadata(artifact);
        return metadata != null ? metadata.secret() : globalSecret;
    }

    public record ArtifactMetadata(
            Artifact artifact,
            String secret
    ) {}
}
