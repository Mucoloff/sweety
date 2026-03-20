package dev.sweety.versioning.server.logic.release;

import dev.sweety.versioning.server.logic.storage.Storage;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;

public class ReleaseState {

    final Object lock = new Object();

    private final EnumMap<Channel, Deque<ReleaseInfo>> history = new EnumMap<>(Channel.class);
    private final EnumMap<Channel, ReleaseInfo> latest = new EnumMap<>(Channel.class);

    private final Path metadata;
    private final Path root;

    ReleaseState(Artifact artifact, Storage storage) {
        this.metadata = storage.metadata().get(artifact);
        this.root = storage.artifacts().get(artifact);
        for (Channel channel : Channel.values()) {
            history.put(channel, new ArrayDeque<>());
        }
    }

    public Deque<ReleaseInfo> history(Channel channel) {
        return history.get(channel);
    }

    public ReleaseInfo latest(Channel channel) {
        return latest.get(channel);
    }

    public void latest(Channel channel, ReleaseInfo info){
        this.latest.put(channel, info);
    }

    public Path metadata() {
        return metadata;
    }

    public Path root() {
        return root;
    }
}
