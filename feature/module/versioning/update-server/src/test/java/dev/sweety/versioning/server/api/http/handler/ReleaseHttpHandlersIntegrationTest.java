package dev.sweety.versioning.server.api.http.handler;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.client.http.HttpCachingReleaseService;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.adapter.in.http.BaseJarReleaseHttpHandler;
import dev.sweety.versioning.server.adapter.in.http.LatestReleaseHttpHandler;
import dev.sweety.versioning.server.adapter.out.storage.FileReleaseRepository;
import dev.sweety.versioning.server.application.release.ReleaseManager;
import dev.sweety.versioning.server.adapter.out.storage.Storage;
import dev.sweety.versioning.version.ReleaseInfo;
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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseHttpHandlersIntegrationTest {

    private String prevKey;

    @BeforeEach
    void saveKey() {
        prevKey = Settings.RELEASE_API_KEY;
    }

    @AfterEach
    void restoreKey() {
        Settings.RELEASE_API_KEY = prevKey != null ? prevKey : "";
    }

    @Test
    void latestAndBaseJar_handlers(@TempDir Path tmp) throws Exception {
        Storage storage = new Storage(tmp);
        ReleaseManager rm = new ReleaseManager(storage, new FileReleaseRepository());
        byte[] jar = new byte[]{0x50, 0x4b, 0x03, 0x04};
        ReleaseInfo applied = rm.applyRelease(
                new Artifact("PLUG"),
                Channel.STABLE,
                new Version(3, 1, 0),
                1.0f,
                jar);
        assertNotNull(applied);

        Settings.RELEASE_API_KEY = "integration-key";

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/release/latest", new LatestReleaseHttpHandler(rm));
        server.createContext("/release/base-jar", new BaseJarReleaseHttpHandler(rm));
        server.start();
        try {
            int port = server.getAddress().getPort();
            URI base = URI.create("http://127.0.0.1:" + port);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest latestReq = HttpRequest.newBuilder(base.resolve("/release/latest?artifact=PLUG&channel=stable")).GET().build();
            HttpResponse<String> latestRes = client.send(latestReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, latestRes.statusCode());
            ReleaseInfo parsed = HttpCachingReleaseService.parseReleaseJson(latestRes.body());
            assertEquals(new Version(3, 1, 0), parsed.version());

            HttpRequest jarReq = HttpRequest.newBuilder(base.resolve("/release/base-jar?artifact=PLUG&channel=stable&version=3.1.0"))
                    .header("X-Sweety-Release-Key", "integration-key")
                    .GET()
                    .build();
            HttpResponse<Path> jarRes = client.send(jarReq, HttpResponse.BodyHandlers.ofFile(tmp.resolve("dl.jar")));
            assertEquals(200, jarRes.statusCode());
            assertArrayEquals(jar, Files.readAllBytes(jarRes.body()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void baseJar_withoutKey_returns403(@TempDir Path tmp) throws Exception {
        Storage storage = new Storage(tmp);
        ReleaseManager rm = new ReleaseManager(storage, new FileReleaseRepository());
        rm.applyRelease(new Artifact("P2"), Channel.STABLE, new Version(1, 0, 0), 1f, new byte[]{7});

        Settings.RELEASE_API_KEY = "";

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/release/base-jar", new BaseJarReleaseHttpHandler(rm));
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest jarReq = HttpRequest.newBuilder(
                            base.resolve("/release/base-jar?artifact=P2&channel=stable&version=1.0.0"))
                    .header("X-Sweety-Release-Key", "x")
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(jarReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, res.statusCode());
        } finally {
            server.stop(0);
        }
    }
}
