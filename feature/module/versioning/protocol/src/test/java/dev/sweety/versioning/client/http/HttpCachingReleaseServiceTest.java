package dev.sweety.versioning.client.http;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpCachingReleaseServiceTest {

    @Test
    void latestAndResolveBaseJar_usesServerAndCache(@TempDir Path tmp) throws Exception {
        byte[] jar = new byte[]{9, 8, 7};

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/release/latest", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            String json = "{\"version\":\"2.0.0\",\"channel\":\"stable\",\"updatedAt\":\"2024-01-01T00:00:00Z\",\"rollout\":1.0}";
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.createContext("/release/base-jar", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            String key = ex.getRequestHeaders().getFirst("X-Sweety-Release-Key");
            if (!"k".equals(key)) {
                ex.sendResponseHeaders(403, -1);
                ex.close();
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/java-archive");
            ex.sendResponseHeaders(200, jar.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(jar);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            URI base = URI.create("http://127.0.0.1:" + port);
            Path cache = tmp.resolve("cache");
            HttpCachingReleaseService svc = new HttpCachingReleaseService(base, "k", cache);

            ReleaseInfo info = svc.latest(Artifact.APP, Channel.STABLE);
            assertEquals(new Version(2, 0, 0), info.version());

            Path p1 = svc.resolveBaseJar(Artifact.APP, Channel.STABLE, new Version(2, 0, 0));
            assertArrayEquals(jar, Files.readAllBytes(p1));
            Path p2 = svc.resolveBaseJar(Artifact.APP, Channel.STABLE, new Version(2, 0, 0));
            assertEquals(p1.toAbsolutePath(), p2.toAbsolutePath());
        } finally {
            server.stop(0);
        }
    }
}
