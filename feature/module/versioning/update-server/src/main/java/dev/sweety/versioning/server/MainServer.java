package dev.sweety.versioning.server;

import com.google.gson.JsonObject;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.thread.ProfileThread;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.server.logic.cache.CacheManager;
import dev.sweety.versioning.server.logic.client.ClientRegistry;
import dev.sweety.versioning.server.logic.download.DownloadManager;
import dev.sweety.versioning.server.api.http.HttpUpdateServer;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.logic.storage.Storage;
import dev.sweety.versioning.server.api.netty.NettyUpdateServer;
import dev.sweety.versioning.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MainServer {

    public static void main(String[] args) throws IOException {
        int port = 8081;//Integer.parseInt(System.getenv().getOrDefault("UPDATE_SERVER_PORT", "8080"));

        final Storage storage = new Storage();
        loadSettings(storage.settings(), storage.temp());

        final ReleaseManager releaseManager = new ReleaseManager(storage);
        final PatchManager patchManager = new PatchManager(storage, releaseManager);
        final CacheManager cacheManager = new CacheManager(storage);
        final ClientRegistry clientRegistry = new ClientRegistry();
        final DownloadManager downloadManager = new DownloadManager();

        final HttpUpdateServer httpServer = new HttpUpdateServer(port, Settings.ROLLBACK_TOKEN, Settings.WEBHOOK_SECRET, releaseManager,patchManager, downloadManager, cacheManager, clientRegistry);
        final ProfileThread t = new ProfileThread("http");

        Runnable stop = () -> {
            httpServer.stop(0);
            t.shutdown();
        };

        final NettyUpdateServer nettyServer = new NettyUpdateServer("localhost", 9901, PacketRegistry.REGISTRY, downloadManager, releaseManager, patchManager, stop);

        httpServer.setRelease(nettyServer::broadcastRelease);
        httpServer.setRollback(nettyServer::broadcastRollback);

        t.execute(httpServer::start);

        Messenger.init(nettyServer);
    }

    private static void loadSettings(final Path settingFile, final Path tempDir) throws IOException {
        if (!Files.exists(settingFile)) {
            final JsonObject root = new JsonObject();

            root.addProperty("ROLLBACK_TOKEN", Settings.ROLLBACK_TOKEN);
            root.addProperty("WEBHOOK_SECRET", Settings.WEBHOOK_SECRET);
            root.addProperty("TOKEN_GEN_SALT", Settings.TOKEN_GEN_SALT);
            root.addProperty("PERCENT_SIZE", Settings.PERCENT_SIZE);
            root.addProperty("MAX_PATCH_VER_DISTANCE", Settings.MAX_PATCH_VER_DISTANCE);

            Path tmp = tempDir.resolve(settingFile.getFileName() + ".tmp");
            Files.writeString(tmp, Utils.GSON.toJson(root));
            Files.move(tmp, settingFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }

        final JsonObject root = Utils.GSON.fromJson(Files.readString(settingFile), JsonObject.class);

        Settings.ROLLBACK_TOKEN = root.get("ROLLBACK_TOKEN").getAsString();
        Settings.WEBHOOK_SECRET = root.get("WEBHOOK_SECRET").getAsString();
        Settings.TOKEN_GEN_SALT = root.get("TOKEN_GEN_SALT").getAsString();
        Settings.PERCENT_SIZE = root.get("PERCENT_SIZE").getAsFloat();
        Settings.MAX_PATCH_VER_DISTANCE = root.get("MAX_PATCH_VER_DISTANCE").getAsInt();
    }

}
