package dev.sweety.versioning.server.api.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.client.http.HttpTokenDownloadReleaseService;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.adapter.out.cache.CacheManager;
import dev.sweety.versioning.server.domain.client.ClientRegistry;
import dev.sweety.versioning.server.logic.download.DownloadHandler;
import dev.sweety.versioning.server.logic.download.DownloadManager;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.adapter.out.storage.FileReleaseRepository;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.adapter.out.storage.Storage;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseDownloadTokenHandlerIntegrationTest {

    private String prevKey;

    @BeforeEach
    void save() {
        prevKey = Settings.RELEASE_API_KEY;
    }

    @AfterEach
    void restore() {
        Settings.RELEASE_API_KEY = prevKey != null ? prevKey : "";
    }

    @Test
    void reserveThenDownload_deliversJarBytes(@TempDir Path tmp) throws Exception {
        Storage storage = new Storage(tmp);
        ReleaseManager rm = new ReleaseManager(storage, new FileReleaseRepository());
        byte[] jar = new byte[]{0x50, 0x4b, 0x03, 0x04};
        assertNotNull(rm.applyRelease(new Artifact("TOK"), Channel.STABLE, new Version(4, 0, 1), 1f, jar));

        DownloadManager dm = new DownloadManager();
        CacheManager cache = new CacheManager(storage);
        ClientRegistry clients = new ClientRegistry();
        PatchManager patches = new PatchManager(storage, rm);

        Settings.RELEASE_API_KEY = "tok-key";

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/release/download-token", new ReleaseDownloadTokenHandler(dm, rm));
        server.createContext("/download", new DownloadHandler(dm, cache, clients, rm, patches));
        server.start();
        try {
            int port = server.getAddress().getPort();
            URI base = URI.create("http://127.0.0.1:" + port);
            UUID client = UUID.randomUUID();

            JsonObject in = new JsonObject();
            in.addProperty("clientId", client.toString());
            in.addProperty("artifact", "TOK");
            in.addProperty("channel", "STABLE");
            in.addProperty("version", "4.0.1");

            HttpClient http = HttpClient.newHttpClient();
            HttpRequest post = HttpRequest.newBuilder(base.resolve("/release/download-token"))
                    .header("Content-Type", "application/json")
                    .header("X-Sweety-Release-Key", "tok-key")
                    .POST(HttpRequest.BodyPublishers.ofString(Utils.gson().toJson(in), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(post, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode());
            JsonObject out = Utils.gson().fromJson(res.body(), JsonObject.class);
            String path = out.get("downloadPath").getAsString();
            assertTrue(path.startsWith("/download?"));

            URI get = base.resolve(path);
            HttpResponse<Path> dl = http.send(HttpRequest.newBuilder(get).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(tmp.resolve("out.jar")));
            assertEquals(200, dl.statusCode());
            assertTrue(Files.size(dl.body()) > 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientHttpTokenDownloadService_roundTrip(@TempDir Path tmp) throws Exception {
        Storage storage = new Storage(tmp);
        ReleaseManager rm = new ReleaseManager(storage, new FileReleaseRepository());
        byte[] jar = new byte[]{4, 5, 6};
        assertNotNull(rm.applyRelease(new Artifact("EXTT"), Channel.BETA, new Version(1, 1, 0), 1f, jar));

        DownloadManager dm = new DownloadManager();
        CacheManager cache = new CacheManager(storage);
        ClientRegistry clients = new ClientRegistry();
        PatchManager patches = new PatchManager(storage, rm);
        Settings.RELEASE_API_KEY = "svc-key";

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/release/latest", new LatestReleaseHttpHandler(rm));
        server.createContext("/release/download-token", new ReleaseDownloadTokenHandler(dm, rm));
        server.createContext("/download", new DownloadHandler(dm, cache, clients, rm, patches));
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            UUID clientId = UUID.randomUUID();
            Path cacheDir = tmp.resolve("client-cache");
            HttpTokenDownloadReleaseService svc = new HttpTokenDownloadReleaseService(base, "svc-key", clientId, cacheDir);
            assertEquals(new Version(1, 1, 0), svc.latest(new Artifact("EXTT"), Channel.BETA).version());
            Path p = svc.resolveBaseJar(new Artifact("EXTT"), Channel.BETA, new Version(1, 1, 0));
            byte[] got = Files.readAllBytes(p);
            assertTrue(got.length >= jar.length, "downloaded jar should be at least as large as base payload");
            Path p2 = svc.resolveBaseJar(new Artifact("EXTT"), Channel.BETA, new Version(1, 1, 0));
            assertEquals(p.toAbsolutePath(), p2.toAbsolutePath());
        } finally {
            server.stop(0);
        }
    }
}
