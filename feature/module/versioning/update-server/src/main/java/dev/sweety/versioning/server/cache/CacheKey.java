package dev.sweety.versioning.server.cache;

import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.file.Path;
import java.util.UUID;

public record CacheKey(Artifact artifact, Version version, Channel channel, UUID clientId) {

    public Path toPath(Path root, String extension) {
        final Path channelDir = root.resolve(channel().name().toLowerCase());
        final Path versionDir = version().resolve(channelDir);
        return versionDir.resolve(clientId() + "." + extension);
    }

    public Path toPath(Path root) {
        return toPath(root, "jar");
    }

}