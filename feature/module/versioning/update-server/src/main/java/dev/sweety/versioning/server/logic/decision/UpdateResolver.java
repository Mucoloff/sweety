package dev.sweety.versioning.server.logic.decision;

import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.api.netty.ForcedUpdate;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;

public final class UpdateResolver {

    public static UpdateDecision resolve(
            UUID clientId,
            Channel clientChannel,
            Artifact artifact,
            Version current,
            ReleaseInfo latest,
            float rollout,
            @Nullable ForcedUpdate forcedUpdate,
            PatchManager patchManager,
            ReleaseManager releaseManager
    ) {

        // 1. forced override
        if (forcedUpdate != null && clientChannel.accepts(forcedUpdate.channel())) {
            Version target = forcedUpdate.target();

            if (!current.equals(target)) {
                return new UpdateDecision(
                        true,
                        target,
                        DownloadType.FULL,
                        true
                );
            }
        }

        // 2. normal release flow
        Version latestVersion = latest.version();
        Channel releaseChannel = latest.channel();

        boolean channelOk = clientChannel.accepts(releaseChannel);
        if (!channelOk) {
            return new UpdateDecision(false, current, null, false);
        }

        boolean newer = latestVersion.newerThan(current);

        boolean rolloutOk = inRollout(clientId, artifact, latestVersion, rollout);

        boolean shouldUpdate = newer && rolloutOk;

        if (!shouldUpdate) {
            return new UpdateDecision(false, current, null, false);
        }

        // 3. choose download type
        DownloadType type = chooseType(artifact, releaseChannel, latestVersion, current, patchManager, releaseManager);

        return new UpdateDecision(
                true,
                latestVersion,
                type,
                false
        );
    }

    private static boolean inRollout(UUID clientId, Artifact artifact, Version version, float rollout) {
        String seed = clientId + ":" + artifact.name() + ":" + version;

        CRC32 crc = new CRC32();
        crc.update(seed.getBytes(StandardCharsets.UTF_8));

        long bucket = crc.getValue() % 100;
        return bucket < (rollout * 100.0f);
    }

    private static DownloadType chooseType(
            Artifact artifact,
            Channel releaseChannel,
            Version version,
            Version current,
            PatchManager patchManager,
            ReleaseManager releaseManager
    ) {
        if (Version.ZERO.equals(current)) return DownloadType.FULL;

        final long size;
        try {
            size = releaseManager.resolveBaseJar(artifact, releaseChannel, version).toFile().length();
        } catch (Exception e) {
            return DownloadType.FULL;
        }

        final Optional<File> cachedPatch = patchManager.cached(artifact, releaseChannel, version, current);

        if (cachedPatch.isEmpty()) return DownloadType.FULL;

        if (cachedPatch.get().length() >= size * Settings.PERCENT_SIZE) {
            return DownloadType.FULL;
        }

        return DownloadType.PATCH;
    }
}