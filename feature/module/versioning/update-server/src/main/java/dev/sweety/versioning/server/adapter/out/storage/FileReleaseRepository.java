package dev.sweety.versioning.server.adapter.out.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.server.logic.release.ReleaseState;
import dev.sweety.versioning.server.port.out.ReleaseRepository;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public class FileReleaseRepository implements ReleaseRepository {
    private static final SimpleLogger LOGGER = new SimpleLogger(FileReleaseRepository.class);

    @Override
    public void load(Artifact artifact, ReleaseState state) throws IOException {
        if (!Files.exists(state.metadata())) {
            for (Channel channel : Channel.values()) state.latest(channel, ReleaseInfo.DEFAULT(channel));
            save(artifact, state);
            return;
        }

        JsonObject root = Utils.gson().fromJson(Files.readString(state.metadata()), JsonObject.class);

        for (Channel channel : Channel.values()) {
            JsonObject channelEntry = root.getAsJsonObject(channel.prettyName());
            if (channelEntry == null) continue;

            JsonObject latest = channelEntry.getAsJsonObject("latest");
            JsonArray hist = channelEntry.getAsJsonArray("history");
            if (hist != null) {
                hist.asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .map(this::deserialize)
                        .filter(info -> {
                            if (info.channel() == channel) return true;
                            LOGGER.warn("Invalid channel for release " + info + ", expected " + channel);
                            return false;
                        })
                        .forEach(state.history(channel)::addLast);
            }
            state.latest(channel, deserialize(latest));
        }
    }

    @Override
    public void save(Artifact artifact, ReleaseState state) throws IOException {
        JsonObject root = new JsonObject();

        for (Channel channel : Channel.values()) {
            JsonObject channelEntry = new JsonObject();
            channelEntry.add("latest", serialize(state.latest(channel)));

            JsonArray hist = new JsonArray();
            state.history(channel).stream()
                    .map(this::serialize)
                    .forEach(hist::add);

            channelEntry.add("history", hist);
            root.add(channel.prettyName(), channelEntry);
        }

        Path tmpFile = Storage.temp(state.metadata());
        Files.writeString(tmpFile, Utils.gson().toJson(root));
        Files.move(tmpFile, state.metadata(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private JsonObject serialize(ReleaseInfo info) {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", info.version().toString());
        obj.addProperty("channel", info.channel().prettyName());
        obj.addProperty("updatedAt", info.updatedAt().toString());
        obj.addProperty("rollout", info.rollout());
        return obj;
    }

    private ReleaseInfo deserialize(JsonObject obj) {
        return new ReleaseInfo(
                Version.parse(obj.get("version").getAsString()),
                Channel.valueOf(obj.get("channel").getAsString().toUpperCase()),
                Float.parseFloat(obj.get("rollout").getAsString()),
                Instant.parse(obj.get("updatedAt").getAsString())
        );
    }
}
