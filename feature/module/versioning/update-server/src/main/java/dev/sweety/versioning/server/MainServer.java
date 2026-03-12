package dev.sweety.versioning.server;

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

import java.io.IOException;

public class MainServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;//Integer.parseInt(System.getenv().getOrDefault("UPDATE_SERVER_PORT", "8080"));

        final Storage storage = new Storage();

        final ReleaseManager releaseManager = new ReleaseManager(storage);
        final CacheManager cacheManager = new CacheManager(storage);
        final ClientRegistry clientRegistry = new ClientRegistry();
        final DownloadManager downloadManager = new DownloadManager();

        final HttpUpdateServer httpServer = new HttpUpdateServer(port, "token", "secret", releaseManager, downloadManager, cacheManager, clientRegistry);
        final ProfileThread t = new ProfileThread("http");

        Runnable stop = () -> {
            httpServer.stop(0);
            t.shutdown();
        };
        final NettyUpdateServer nettyServer = new NettyUpdateServer("localhost", 9900, PacketRegistry.REGISTRY, downloadManager, releaseManager, stop);

        httpServer.setBroadcast(nettyServer::broadcastRelease);

        t.execute(httpServer::start);

        Messenger.init(nettyServer);
    }

}
