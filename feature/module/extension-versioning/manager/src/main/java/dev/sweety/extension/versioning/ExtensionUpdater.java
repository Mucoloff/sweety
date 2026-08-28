package dev.sweety.extension.versioning;

import dev.sweety.i18n.Messages;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.version.ReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Checks extensions against a remote or local {@link ReleaseService} and stages {@code .update} files
 * next to loaded JARs. For HTTP, use {@link RemoteReleaseSupport#http}, {@link RemoteReleaseSupport#httpWithTokenDownload},
 * or {@link RemoteReleaseSupport#fromEnvironment}.
 */
public class ExtensionUpdater<T extends VersionableExtension> {

    private static final SimpleLogger LOGGER = SimpleLogger.of(ExtensionUpdater.class);
    private static final Messages MESSAGES = Messages.forBundle("messages");
    /** Upper bound for waiting on all parallel extension update checks (per {@link #updateAll(Channel)}). */
    private static final long UPDATE_ALL_TIMEOUT_MINUTES = 30L;

    private final UpdateableExtensionManager<T> manager;
    private final ReleaseService releaseService;
    private final Consumer<Path> afterDownload;

    public ExtensionUpdater(UpdateableExtensionManager<T> manager, ReleaseService releaseService) {
        this(manager, releaseService, null);
    }

    /**
     * @param afterDownload called with the staged update jar after a successful download.
     *                      Use to wire {@code PendingUpdateApplier.INSTANCE::schedulePendingUpdate}
     *                      from the bootstrap side (keeping api ↔ bootstrap boundary intact).
     */
    public ExtensionUpdater(UpdateableExtensionManager<T> manager, ReleaseService releaseService,
                            Consumer<Path> afterDownload) {
        this.manager = manager;
        this.releaseService = releaseService;
        this.afterDownload = afterDownload;
    }

    public CompletableFuture<UpdateOutcome> updateIfAvailable(@NotNull T extension, @NotNull Channel channel) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ReleaseInfo latest = releaseService.latest(extension.artifact(), channel);
                if (latest == null) return UpdateOutcome.upToDate();

                Version current = Version.parse(extension.version());
                if (latest.version().newerThan(current)) {
                    LOGGER.info(MESSAGES.get("update.checking", extension.name(), current, latest.version()));
                    return downloadAndPrepareUpdate(extension, latest) ? UpdateOutcome.updated() : UpdateOutcome.upToDate();
                }

                return UpdateOutcome.upToDate();
            } catch (Exception e) {
                LOGGER.error(MESSAGES.get("update.checkFailed", extension.name()), e);
                return UpdateOutcome.failed(e);
            }
        });
    }

    private boolean downloadAndPrepareUpdate(T extension, ReleaseInfo release) throws IOException {
        Path newJar = releaseService.resolveBaseJar(extension.artifact(), release.channel(), release.version());
        if (!Files.exists(newJar)) {
            LOGGER.error(MESSAGES.get("update.remoteJarMissing", extension.artifact().name()));
            return false;
        }

        Path currentFile = manager.jarPath(extension);
        if (currentFile == null) {
            LOGGER.error(MESSAGES.get("update.localUnresolved", extension.name()));
            return false;
        }

        Path targetPath = currentFile;
        Path updatePath = targetPath.resolveSibling(targetPath.getFileName() + UpdateableExtensionManager.UPDATE_SUFFIX);

        Files.copy(newJar, updatePath, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info(MESSAGES.get("update.downloaded", extension.name()));
        if (afterDownload != null) afterDownload.accept(updatePath);
        return true;
    }

    /**
     * Checks all loaded extensions for updates in parallel, waits for completion or timeout, and logs per-extension failures.
     *
     * @return completes when all update tasks finish, or completes exceptionally on timeout
     */
    public CompletableFuture<Void> updateAll(@NotNull Channel channel) {
        Objects.requireNonNull(channel, "channel cannot be null");
        //noinspection unchecked
        CompletableFuture<UpdateOutcome>[] futures = manager.extensions().values().stream()
                .map(ext -> updateIfAvailable(ext, channel).handle((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error(MESSAGES.get("update.taskFailed", ext.name()), ex);
                        return UpdateOutcome.failed(ex);
                    }
                    return result != null ? result : UpdateOutcome.upToDate();
                }))
                .toArray(CompletableFuture[]::new);
        if (futures.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures).orTimeout(UPDATE_ALL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }
}
