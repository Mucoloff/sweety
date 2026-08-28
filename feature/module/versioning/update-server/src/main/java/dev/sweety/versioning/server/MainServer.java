package dev.sweety.versioning.server;

import com.google.gson.JsonObject;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.thread.ProfileThread;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.net.http.HttpUpdateServerBootstrap;
import dev.sweety.versioning.server.net.netty.NettyUpdateServer;
import dev.sweety.versioning.server.store.CacheManager;
import dev.sweety.versioning.server.store.DownloadSessionRegistry;
import dev.sweety.versioning.server.security.ArtifactSigner;
import dev.sweety.versioning.server.store.FileReleaseRepository;
import dev.sweety.versioning.server.store.Storage;
import dev.sweety.versioning.server.store.InMemoryDownloadTokenStore;
import dev.sweety.versioning.server.service.PatchManager;
import dev.sweety.versioning.server.service.ReleaseManager;
import dev.sweety.versioning.server.data.ArtifactRegistry;
import dev.sweety.versioning.server.data.ClientRegistry;
import dev.sweety.versioning.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Embeds {@link HttpUpdateServerBootstrap} (port argument {@code 8080} by default in {@link #main}) and {@link NettyUpdateServer}.
 * Loads {@link Settings} from {@link Storage#settings()}, creating the defaults file on first run. See {@link Settings} for env keys and HTTP/Netty security.
 */
public class MainServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;

        final Storage storage = new Storage();
        loadSettings(storage.settings());

        final ArtifactRegistry artifactRegistry = new ArtifactRegistry(Settings.WEBHOOK_SECRET);
        final ReleaseManager releaseManager = new ReleaseManager(storage, new FileReleaseRepository());
        final PatchManager patchManager = new PatchManager(storage, releaseManager);
        final CacheManager cacheManager = new CacheManager(storage);
        final ClientRegistry clientRegistry = new ClientRegistry();
        final InMemoryDownloadTokenStore downloadTokenStore = new InMemoryDownloadTokenStore();

        final ArtifactSigner signer = ArtifactSigner.of(
                Settings.INTEGRITY_HMAC_SECRET,
                Settings.ED25519_KEY_PATH.isBlank()
                        ? System.getenv().getOrDefault("LUCE_ED25519_KEY", "")
                        : Settings.ED25519_KEY_PATH
        );
        final DownloadSessionRegistry downloadSessions = new DownloadSessionRegistry(signer);

        final HttpUpdateServerBootstrap httpServer = new HttpUpdateServerBootstrap(
                port,
                Settings.ROLLBACK_TOKEN,
                artifactRegistry,
                releaseManager,
                releaseManager,
                releaseManager,
                patchManager,
                downloadTokenStore,
                cacheManager,
                clientRegistry,
                signer,
                downloadSessions
        );
        final ProfileThread t = new ProfileThread("http");

        Runnable stop = () -> {
            httpServer.stop(0);
            t.shutdown();
        };

        final NettyUpdateServer nettyServer = new NettyUpdateServer("localhost", 9900, PacketRegistry.REGISTRY, downloadTokenStore, releaseManager, patchManager, downloadSessions, stop);

        httpServer.setBroadcast(nettyServer);

        t.execute(httpServer::start);

        Messenger.init(nettyServer);
    }

    private static void loadSettings(final Path settingFile) throws IOException {
        if (!Files.exists(settingFile)) {
            Path tmp = Storage.temp(settingFile);
            try (OutputStream os = Files.newOutputStream(tmp)) {
                os.write(root().getBytes(StandardCharsets.UTF_8));
            }
            Files.move(tmp, settingFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }

        final String settingJson;
        try (InputStream in = Files.newInputStream(settingFile)) {
            settingJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final JsonObject root = Utils.gson().fromJson(settingJson, JsonObject.class);
        load(root);
    }

    private static void load(@NotNull JsonObject root) {
        Settings.ROLLBACK_TOKEN = root.get("ROLLBACK_TOKEN").getAsString();
        Settings.WEBHOOK_SECRET = root.get("WEBHOOK_SECRET").getAsString();
        Settings.TOKEN_GEN_SALT = root.get("TOKEN_GEN_SALT").getAsString();
        Settings.PERCENT_SIZE = root.get("PERCENT_SIZE").getAsFloat();
        Settings.MAX_PATCH_VER_DISTANCE = root.get("MAX_PATCH_VER_DISTANCE").getAsInt();
        Settings.DOWNLOAD_SPEED = root.get("DOWNLOAD_SPEED").getAsFloat();
        Settings.DEFAULT_TTL = root.get("DEFAULT_TTL").getAsLong();
        Settings.DOWNLOAD_EXPIRE_DELAY_MS = root.get("DOWNLOAD_EXPIRE_DELAY_MS").getAsLong();
        Settings.MAX_CONCURRENT_DOWNLOADS = root.get("MAX_CONCURRENT_DOWNLOADS").getAsInt();
        Settings.HISTORY_LIMIT = root.get("HISTORY_LIMIT").getAsInt();
        Settings.GLOBAL_RATE_LIMIT = root.get("GLOBAL_RATE_LIMIT").getAsInt();
        Settings.PER_IP_RATE_LIMIT = root.get("PER_IP_RATE_LIMIT").getAsInt();
        Settings.RATE_LIMIT_WINDOW = root.get("RATE_LIMIT_WINDOW").getAsLong();
        if (root.has("NETTY_HANDSHAKE_SECRET") && !root.get("NETTY_HANDSHAKE_SECRET").isJsonNull()) {
            Settings.NETTY_HANDSHAKE_SECRET = root.get("NETTY_HANDSHAKE_SECRET").getAsString();
        } else {
            Settings.NETTY_HANDSHAKE_SECRET = System.getenv().getOrDefault("NETTY_HANDSHAKE_SECRET", "");
        }
        if (root.has("RELEASE_API_KEY") && !root.get("RELEASE_API_KEY").isJsonNull()) {
            Settings.RELEASE_API_KEY = root.get("RELEASE_API_KEY").getAsString();
        } else {
            Settings.RELEASE_API_KEY = System.getenv().getOrDefault("RELEASE_API_KEY", "");
        }
        if (root.has("INTEGRITY_HMAC_SECRET") && !root.get("INTEGRITY_HMAC_SECRET").isJsonNull()) {
            Settings.INTEGRITY_HMAC_SECRET = root.get("INTEGRITY_HMAC_SECRET").getAsString();
        } else {
            Settings.INTEGRITY_HMAC_SECRET = System.getenv().getOrDefault("INTEGRITY_HMAC_SECRET", "");
        }
        if (root.has("ED25519_KEY_PATH") && !root.get("ED25519_KEY_PATH").isJsonNull()) {
            Settings.ED25519_KEY_PATH = root.get("ED25519_KEY_PATH").getAsString();
        } else {
            Settings.ED25519_KEY_PATH = System.getenv().getOrDefault("LUCE_ED25519_KEY", "");
        }
    }

    private static @NotNull String root() {
        final JsonObject root = new JsonObject();

        root.addProperty("ROLLBACK_TOKEN", Settings.ROLLBACK_TOKEN);
        root.addProperty("WEBHOOK_SECRET", Settings.WEBHOOK_SECRET);
        root.addProperty("TOKEN_GEN_SALT", Settings.TOKEN_GEN_SALT);

        root.addProperty("PERCENT_SIZE", Settings.PERCENT_SIZE);
        root.addProperty("MAX_PATCH_VER_DISTANCE", Settings.MAX_PATCH_VER_DISTANCE);
        root.addProperty("DOWNLOAD_SPEED", Settings.DOWNLOAD_SPEED);
        root.addProperty("DEFAULT_TTL", Settings.DEFAULT_TTL);

        root.addProperty("DOWNLOAD_EXPIRE_DELAY_MS", Settings.DOWNLOAD_EXPIRE_DELAY_MS);
        root.addProperty("MAX_CONCURRENT_DOWNLOADS", Settings.MAX_CONCURRENT_DOWNLOADS);

        root.addProperty("HISTORY_LIMIT", Settings.HISTORY_LIMIT);

        root.addProperty("GLOBAL_RATE_LIMIT", Settings.GLOBAL_RATE_LIMIT);
        root.addProperty("PER_IP_RATE_LIMIT", Settings.PER_IP_RATE_LIMIT);
        root.addProperty("RATE_LIMIT_WINDOW", Settings.RATE_LIMIT_WINDOW);
        root.addProperty("NETTY_HANDSHAKE_SECRET", Settings.NETTY_HANDSHAKE_SECRET);
        root.addProperty("RELEASE_API_KEY", Settings.RELEASE_API_KEY);
        root.addProperty("INTEGRITY_HMAC_SECRET", Settings.INTEGRITY_HMAC_SECRET);
        root.addProperty("ED25519_KEY_PATH", Settings.ED25519_KEY_PATH);

        return Utils.gson().toJson(root);
    }
}
