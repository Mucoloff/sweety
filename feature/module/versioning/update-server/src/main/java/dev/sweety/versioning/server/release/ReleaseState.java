package dev.sweety.versioning.server.release;

import dev.sweety.versioning.server.storage.Storage;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

public class ReleaseState {

    final Object lock = new Object();

    final Deque<ReleaseInfo> history = new ArrayDeque<>();

    ReleaseInfo latest;

    final Path metadata;
    final Path base;
    final Path cache;
    final Path tmp;

    ReleaseState(Artifact artifact, Storage storage) {
        this.metadata = storage.metadata().get(artifact);
        this.base = storage.artifacts().get(artifact);
        this.cache = storage.cache().get(artifact);
        this.tmp = storage.tmp().get(artifact);
    }
}
