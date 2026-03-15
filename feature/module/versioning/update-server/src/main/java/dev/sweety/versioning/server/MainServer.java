package dev.sweety.versioning.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.thread.ProfileThread;
import dev.sweety.versioning.protocol.PacketRegistry;
import dev.sweety.versioning.server.cache.CacheManager;
import dev.sweety.versioning.server.client.ClientRegistry;
import dev.sweety.versioning.server.download.DownloadManager;
import dev.sweety.versioning.server.http.HttpUpdateServer;
import dev.sweety.versioning.server.release.ReleaseManager;
import dev.sweety.versioning.server.storage.Storage;
import dev.sweety.versioning.server.updater.NettyUpdateServer;
import dev.sweety.versioning.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MainServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;//Integer.parseInt(System.getenv().getOrDefault("UPDATE_SERVER_PORT", "8080"));

        final Storage storage = new Storage();
        loadSettings(storage.settings(), storage.temp());

        final ReleaseManager releaseManager = new ReleaseManager(storage);
        final CacheManager cacheManager = new CacheManager(storage);
        final ClientRegistry clientRegistry = new ClientRegistry();
        final DownloadManager downloadManager = new DownloadManager();

        final HttpUpdateServer httpServer = new HttpUpdateServer(port, Settings.rollbackToken, Settings.webhookSecret, releaseManager, downloadManager, cacheManager, clientRegistry);
        final ProfileThread t = new ProfileThread("http");

        Runnable stop = () -> {
            httpServer.stop(0);
            t.shutdown();
        };
        final NettyUpdateServer nettyServer = new NettyUpdateServer("localhost", 9900, PacketRegistry.REGISTRY, downloadManager, releaseManager, stop);

        httpServer.setRelease(nettyServer::broadcastRelease);
        httpServer.setRollback(nettyServer::broadcastRollback);

        t.execute(httpServer::start);

        Messenger.init(nettyServer);
    }

    private static void loadSettings(final Path settingFile, final Path tempDir) throws IOException {
        if (!Files.exists(settingFile)) {
            final JsonObject root = new JsonObject();

            root.addProperty("rollbackToken", Settings.rollbackToken);
            root.addProperty("webhookSecret", Settings.webhookSecret);
            root.addProperty("tokenGeneratorSalt", Settings.tokenGeneratorSalt);

            Path tmp = tempDir.resolve(settingFile.getFileName() + ".tmp");
            Files.writeString(tmp, Utils.GSON.toJson(root));
            Files.move(tmp, settingFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }

        final JsonObject root = Utils.GSON.fromJson(Files.readString(settingFile), JsonObject.class);

        Settings.rollbackToken = root.get("rollbackToken").getAsString();
        Settings.webhookSecret = root.get("webhookSecret").getAsString();
        Settings.tokenGeneratorSalt = root.get("tokenGeneratorSalt").getAsString();
    }

}
