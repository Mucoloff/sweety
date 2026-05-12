package dev.sweety.extension.versioning;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionUpdaterTest {

    private static final class TestExtension extends VersionableExtension {
        TestExtension(Path folder) {
            super("MYEXT", "1.0.0", null, folder, new SimpleLogger(TestExtension.class));
        }

        @Override
        public void enable() {}

        @Override
        public void disable() {}
    }

    /** Supplies jar paths without loading a real plugin JAR. */
    private static final class TestManager extends UpdateableExtensionManager<VersionableExtension> {
        private final ConcurrentHashMap<VersionableExtension, Path> paths = new ConcurrentHashMap<>();

        TestManager(Path parent) {
            super(parent, VersionableExtension.class);
        }

        void track(VersionableExtension e, Path jar) {
            paths.put(e, jar);
        }

        @Override
        public Path jarPath(VersionableExtension extension) {
            return paths.getOrDefault(extension, super.jarPath(extension));
        }
    }

    private static final class StubRelease implements IReleaseService {
        ReleaseInfo latest;
        Path remoteJar;

        @Override
        public @Nullable ReleaseInfo latest(@NotNull Artifact artifact, @NotNull Channel channel) {
            return latest;
        }

        @Override
        public @NotNull Collection<ReleaseInfo> history(@NotNull Artifact artifact, @NotNull Channel channel) {
            return Collections.emptyList();
        }

        @Override
        public @NotNull Path resolveBaseJar(@NotNull Artifact artifact, @NotNull Channel channel, @NotNull Version version) {
            return remoteJar;
        }

        @Override
        public @Nullable ReleaseInfo rollback(@NotNull Artifact artifact, @NotNull Channel channel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable ReleaseInfo updateRollout(@NotNull Artifact artifact, @NotNull Channel channel, float rollout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable ReleaseInfo applyRelease(
                @NotNull Artifact artifact,
                @NotNull Channel channel,
                @Nullable Version version,
                @Nullable Float rollout,
                @Nullable byte[] jar) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ReleaseInfo resolveLatest(@NotNull Artifact artifact, @NotNull Channel channel) {
            ReleaseInfo l = latest(artifact, channel);
            return l != null ? l : ReleaseInfo.DEFAULT(channel);
        }
    }

    @Test
    void updateIfAvailable_writesUpdateSiblingWhenRemoteIsNewer(@TempDir Path tmp) throws Exception {
        TestManager manager = new TestManager(tmp);
        TestExtension ext = new TestExtension(tmp);
        Path localJar = tmp.resolve("MYEXT.jar");
        Files.writeString(localJar, "local");

        Path serverCopy = tmp.resolve("server.jar");
        Files.writeString(serverCopy, "v2-bytes");

        StubRelease stub = new StubRelease();
        stub.latest = new ReleaseInfo(new Version(2, 0, 0), Channel.STABLE, 1f, Instant.now());
        stub.remoteJar = serverCopy;

        manager.track(ext, localJar);

        ExtensionUpdater<VersionableExtension> updater = new ExtensionUpdater<>(manager, stub);
        Boolean ok = updater.updateIfAvailable(ext, Channel.STABLE).get();
        assertTrue(Boolean.TRUE.equals(ok));

        Path updateSidecar = tmp.resolve("MYEXT.jar.update");
        assertTrue(Files.isRegularFile(updateSidecar));
        assertEquals("v2-bytes", Files.readString(updateSidecar));
        assertEquals("local", Files.readString(localJar));
    }

    @Test
    void updateIfAvailable_noopWhenUpToDate(@TempDir Path tmp) throws Exception {
        TestManager manager = new TestManager(tmp);
        TestExtension ext = new TestExtension(tmp);
        Path localJar = tmp.resolve("MYEXT.jar");
        Files.createFile(localJar);
        manager.track(ext, localJar);

        StubRelease stub = new StubRelease();
        stub.latest = new ReleaseInfo(new Version(1, 0, 0), Channel.STABLE, 1f, Instant.now());
        stub.remoteJar = localJar;

        ExtensionUpdater<VersionableExtension> updater = new ExtensionUpdater<>(manager, stub);
        Boolean ok = updater.updateIfAvailable(ext, Channel.STABLE).get();
        assertFalse(Boolean.TRUE.equals(ok));
        assertFalse(Files.exists(tmp.resolve("MYEXT.jar.update")));
    }
}
