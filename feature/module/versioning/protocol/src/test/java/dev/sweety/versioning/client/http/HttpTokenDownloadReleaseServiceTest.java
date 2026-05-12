package dev.sweety.versioning.client.http;

import com.sun.net.httpserver.HttpServer;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpTokenDownloadReleaseServiceTest {

    @Test
    void postReserveThenGetDownload(@TempDir Path tmp) throws Exception {
        byte[] payload = new byte[]{1, 2, 3, 4};
        UUID client = UUID.randomUUID();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/release/latest", ex -> {
            String json = "{\"version\":\"9.0.0\",\"channel\":\"stable\",\"updatedAt\":\"2024-06-01T00:00:00Z\",\"rollout\":1.0}";
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.createContext("/release/download-token", ex -> {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            String k = ex.getRequestHeaders().getFirst("X-Sweety-Release-Key");
            if (!"secret".equals(k)) {
                ex.sendResponseHeaders(403, -1);
                return;
            }
            String q = "clientId=" + java.net.URLEncoder.encode(client.toString(), StandardCharsets.UTF_8) + "&token=abc";
            String body = "{\"token\":\"abc\",\"downloadPath\":\"/download?" + q + "\"}";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.createContext("/download", ex -> {
            ex.sendResponseHeaders(200, payload.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            HttpTokenDownloadReleaseService svc = new HttpTokenDownloadReleaseService(base, "secret", client, tmp.resolve("c"));
            assertEquals(new Version(9, 0, 0), svc.latest(Artifact.APP, Channel.STABLE).version());
            Path jar = svc.resolveBaseJar(Artifact.APP, Channel.STABLE, new Version(9, 0, 0));
            assertEquals(payload.length, Files.readAllBytes(jar).length);
        } finally {
            server.stop(0);
        }
    }
}
