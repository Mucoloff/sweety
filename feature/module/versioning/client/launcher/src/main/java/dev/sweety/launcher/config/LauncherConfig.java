package dev.sweety.launcher.config;

import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * @deprecated Moved to {@link dev.sweety.launcher.infra.LauncherConfig}.
 *             This class is a deprecated shim kept for backward compatibility.
 */
@Deprecated
public final class LauncherConfig {

    private LauncherConfig() {}

    /** @deprecated Use {@link dev.sweety.launcher.infra.LauncherConfig#defaults()} */
    @Deprecated
    public static dev.sweety.launcher.infra.LauncherConfig defaults() {
        return dev.sweety.launcher.infra.LauncherConfig.defaults();
    }

    /** @deprecated Use {@link dev.sweety.launcher.infra.LauncherConfig#load(Path)} */
    @Deprecated
    public static dev.sweety.launcher.infra.LauncherConfig load(Path file) throws IOException {
        return dev.sweety.launcher.infra.LauncherConfig.load(file);
    }

    /** @deprecated Use {@link dev.sweety.launcher.infra.LauncherConfig#save(Path, dev.sweety.launcher.infra.LauncherConfig)} */
    @Deprecated
    public static void save(Path file, dev.sweety.launcher.infra.LauncherConfig config) throws IOException {
        dev.sweety.launcher.infra.LauncherConfig.save(file, config);
    }
}
