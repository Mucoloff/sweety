package dev.sweety.versioning.server.port.in;

import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.file.Path;
import java.util.Optional;

public interface ResolvePatchPort {

    Optional<Path> cached(Artifact artifact, Channel channel, Version latest, Version current);
}
