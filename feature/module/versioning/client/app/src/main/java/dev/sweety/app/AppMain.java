package dev.sweety.app;

import dev.sweety.build.BuildInfo;
import dev.sweety.extension.versioning.ExtensionUpdater;
import dev.sweety.extension.versioning.RemoteReleaseSupport;
import dev.sweety.extension.versioning.UpdateableExtensionManager;
import dev.sweety.extension.versioning.VersionableExtension;
import dev.sweety.versioning.version.channel.Channel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

public class AppMain {
    public static void main(String[] args) {
        System.out.println("Hello from App!");
        System.out.println("BuildId=" + BuildInfo.BUILD_ID);
        System.out.println("ChannelId=" + BuildInfo.CHANNEL);
        System.out.println("ClientId=" + BuildInfo.CLIENT_ID);
        System.out.println("Version=" + BuildInfo.VERSION);
        System.out.println("Timestamp=" + BuildInfo.TIMESTAMP);
        new ExampleDiff().exampleMethod();

        if (Boolean.parseBoolean(System.getenv().getOrDefault("EXTENSION_HTTP_DEMO", "false"))) {
            try {
                Path cache = Path.of(System.getProperty("java.io.tmpdir"), "sweety-extension-release-cache");
                UpdateableExtensionManager<VersionableExtension> manager =
                        new UpdateableExtensionManager<>(Path.of("extension-demo-root"), VersionableExtension.class);
                var remote = RemoteReleaseSupport.fromEnvironment(cache);
                ExtensionUpdater<VersionableExtension> updater = new ExtensionUpdater<>(manager, remote);
                Channel ch = Channel.valueOf(BuildInfo.CHANNEL.toUpperCase());
                updater.updateAll(ch).join();
                System.out.println("EXTENSION_HTTP_DEMO: updateAll finished (no extensions loaded is OK).");
            } catch (Exception e) {
                System.out.println("EXTENSION_HTTP_DEMO: skipped — " + e.getMessage());
            }
        }

        if (Boolean.parseBoolean(System.getenv().getOrDefault("EXTENSION_HTTP_TOKEN_DEMO", "false"))) {
            try {
                Path cache = Path.of(System.getProperty("java.io.tmpdir"), "sweety-extension-token-cache");
                UUID clientId = UUID.nameUUIDFromBytes(BuildInfo.CLIENT_ID.getBytes(StandardCharsets.UTF_8));
                UpdateableExtensionManager<VersionableExtension> manager =
                        new UpdateableExtensionManager<>(Path.of("extension-demo-root"), VersionableExtension.class);
                var remote = RemoteReleaseSupport.fromEnvironmentWithTokenDownload(cache, clientId);
                ExtensionUpdater<VersionableExtension> updater = new ExtensionUpdater<>(manager, remote);
                updater.updateAll(Channel.valueOf(BuildInfo.CHANNEL.toUpperCase())).join();
                System.out.println("EXTENSION_HTTP_TOKEN_DEMO: updateAll finished.");
            } catch (Exception e) {
                System.out.println("EXTENSION_HTTP_TOKEN_DEMO: skipped — " + e.getMessage());
            }
        }
    }
}