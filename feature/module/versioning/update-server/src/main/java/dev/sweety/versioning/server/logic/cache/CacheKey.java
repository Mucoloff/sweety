package dev.sweety.versioning.server.logic.cache;

import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.file.Path;
import java.util.UUID;

public record CacheKey(Artifact artifact, Channel channel, Version version, UUID clientId) {

    public Path toPath(Path root, String extension) {
        final Path channelDir = root.resolve(channel().prettyName());
        final Path versionDir = version().resolve(channelDir);
        return versionDir.resolve(clientId() + extension);
    }

    public Path toPath(Path root) {
        return toPath(root, ".jar");
    }

}