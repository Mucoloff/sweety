package dev.sweety.versioning.server;

import com.google.gson.JsonObject;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.thread.ProfileThread;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.server.api.http.HttpUpdateServer;
import dev.sweety.versioning.server.api.netty.NettyUpdateServer;
import dev.sweety.versioning.server.logic.cache.CacheManager;
import dev.sweety.versioning.server.logic.client.ClientRegistry;
import dev.sweety.versioning.server.logic.download.DownloadManager;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.logic.storage.Storage;
import dev.sweety.versioning.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MainServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;//Integer.parseInt(System.getenv().getOrDefault("UPDATE_SERVER_PORT", "8080"));

        final Storage storage = new Storage();
        loadSettings(storage.settings());

        final ReleaseManager releaseManager = new ReleaseManager(storage);
        final PatchManager patchManager = new PatchManager(storage, releaseManager);
        final CacheManager cacheManager = new CacheManager(storage);
        final ClientRegistry clientRegistry = new ClientRegistry();
        final DownloadManager downloadManager = new DownloadManager();

        final HttpUpdateServer httpServer = new HttpUpdateServer(port, Settings.ROLLBACK_TOKEN, Settings.WEBHOOK_SECRET, releaseManager, patchManager, downloadManager, cacheManager, clientRegistry);
        final ProfileThread t = new ProfileThread("http");

        Runnable stop = () -> {
            httpServer.stop(0);
            t.shutdown();
        };

        final NettyUpdateServer nettyServer = new NettyUpdateServer("localhost", 9900, PacketRegistry.REGISTRY, downloadManager, releaseManager, patchManager, stop);

        httpServer.setBroadcast(nettyServer::broadcast);

        t.execute(httpServer::start);

        Messenger.init(nettyServer);
    }

    private static void loadSettings(final Path settingFile) throws IOException {
        if (!Files.exists(settingFile)) {
            Path tmp = Storage.temp(settingFile);
            Files.writeString(tmp, root());
            Files.move(tmp, settingFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }

        final JsonObject root = Utils.gson().fromJson(Files.readString(settingFile), JsonObject.class);
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

        return Utils.gson().toJson(root);
    }

}
