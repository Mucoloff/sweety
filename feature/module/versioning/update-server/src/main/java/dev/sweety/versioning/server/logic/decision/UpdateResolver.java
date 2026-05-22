package dev.sweety.versioning.server.logic.decision;

import dev.sweety.versioning.server.application.patch.PatchManager;
import dev.sweety.versioning.server.domain.client.ForcedUpdate;
import dev.sweety.versioning.server.domain.decision.UpdateDecision;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @deprecated Use {@link dev.sweety.versioning.server.domain.decision.UpdateResolver} directly.
 */
@Deprecated
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
            IReleaseService releaseManager
    ) {
        return dev.sweety.versioning.server.domain.decision.UpdateResolver.resolve(
                clientId, clientChannel, artifact, current, latest, rollout, forcedUpdate, patchManager, releaseManager
        );
    }

    private UpdateResolver() {}
}
