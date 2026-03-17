package dev.sweety.versioning.server.release;

import dev.sweety.versioning.server.storage.Storage;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;

public class ReleaseState {

    final Object lock = new Object();

    final EnumMap<Channel, Deque<ReleaseInfo>> channelHistory = new EnumMap<>(Channel.class);
    private final EnumMap<Channel, ReleaseInfo> latest = new EnumMap<>(Channel.class);

    final Path metadata;
    final Path base;
    final Path cache;
    final Path tmp;

    ReleaseState(Artifact artifact, Storage storage) {
        this.metadata = storage.metadata().get(artifact);
        this.base = storage.artifacts().get(artifact);
        this.cache = storage.cache().get(artifact);
        this.tmp = storage.tmp().get(artifact);
        for (Channel channel : Channel.values()) {
            channelHistory.put(channel, new ArrayDeque<>());
        }
    }

    public Deque<ReleaseInfo> history(Channel channel) {
        return channelHistory.get(channel);
    }

    public ReleaseInfo latest(Channel channel) {
        return latest.get(channel);
    }

    public void latest(Channel channel, ReleaseInfo info){
        this.latest.put(channel, info);
    }
}
