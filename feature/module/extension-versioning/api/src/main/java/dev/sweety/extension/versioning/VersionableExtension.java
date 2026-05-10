package dev.sweety.extension.versioning;

import dev.sweety.extension.Extension;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.version.artifact.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class VersionableExtension extends Extension {

    private final Artifact artifact;

    protected VersionableExtension(@NotNull String name, @NotNull String version, @Nullable String description, @NotNull File folder, @NotNull SimpleLogger logger) {
        super(name, version, description, folder, logger);
        this.artifact = new Artifact(name);
    }

    public @NotNull Artifact artifact() {
        return artifact;
    }
}
