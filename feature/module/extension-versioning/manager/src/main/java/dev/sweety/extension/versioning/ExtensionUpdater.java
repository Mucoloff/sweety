package dev.sweety.extension.versioning;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class ExtensionUpdater<T extends VersionableExtension> {

    private static final SimpleLogger LOGGER = new SimpleLogger(ExtensionUpdater.class);

    private final UpdateableExtensionManager<T> manager;
    private final IReleaseService releaseService;

    public ExtensionUpdater(UpdateableExtensionManager<T> manager, IReleaseService releaseService) {
        this.manager = manager;
        this.releaseService = releaseService;
    }

    public CompletableFuture<Boolean> updateIfAvailable(@NotNull T extension, @NotNull Channel channel) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ReleaseInfo latest = releaseService.latest(extension.artifact(), channel);
                if (latest == null) return false;

                Version current = Version.parse(extension.version());
                if (latest.version().newerThan(current)) {
                    LOGGER.info("Updating " + extension.name() + " from " + current + " to " + latest.version());
                    return downloadAndPrepareUpdate(extension, latest);
                }

                return false;
            } catch (Exception e) {
                LOGGER.error("Failed to check for updates for " + extension.name(), e);
                return false;
            }
        });
    }

    private boolean downloadAndPrepareUpdate(T extension, ReleaseInfo release) throws IOException {
        Path newJar = releaseService.resolveBaseJar(extension.artifact(), release.channel(), release.version());
        if (!Files.exists(newJar)) {
            LOGGER.error("Latest jar not found on server for artifact=" + extension.artifact().name());
            return false;
        }

        File currentFile = manager.getFile(extension);
        if (currentFile == null) {
            LOGGER.error("Could not resolve local file for extension " + extension.name());
            return false;
        }

        Path targetPath = currentFile.toPath();
        Path updatePath = targetPath.resolveSibling(targetPath.getFileName() + ".update");

        Files.copy(newJar, updatePath, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("Update for " + extension.name() + " downloaded. It will be applied on next restart.");
        return true;
    }

    public void updateAll(@NotNull Channel channel) {
        manager.extensions().values().forEach(ext -> updateIfAvailable(ext, channel));
    }
}
