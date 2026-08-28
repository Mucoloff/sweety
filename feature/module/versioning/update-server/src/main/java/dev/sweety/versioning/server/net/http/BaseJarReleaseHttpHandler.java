package dev.sweety.versioning.server.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.util.http.HttpUtils;
import dev.sweety.versioning.version.ReleaseService;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * {@code GET /release/base-jar?artifact=APP&channel=stable&version=1.0.0} — raw base artifact jar.
 * Requires non-empty {@link Settings#RELEASE_API_KEY} on the server and header {@code X-Luce-Release-Key}.
 */
public final class BaseJarReleaseHttpHandler implements HttpHandler {

    private final ReleaseService releases;

    public BaseJarReleaseHttpHandler(ReleaseService releases) {
        this.releases = releases;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtils.sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (Settings.RELEASE_API_KEY == null || Settings.RELEASE_API_KEY.isEmpty()) {
            HttpUtils.sendText(exchange, 403, "release jar API disabled");
            return;
        }
        String headerKey = exchange.getRequestHeaders().getFirst("X-Luce-Release-Key");
        if (headerKey == null || !HttpUtils.constantTimeEquals(headerKey, Settings.RELEASE_API_KEY)) {
            HttpUtils.sendText(exchange, 403, "forbidden");
            return;
        }
        Map<String, String> q = HttpUtils.parseQuery(exchange.getRequestURI().getRawQuery());
        String art = q.get("artifact");
        String ch = q.get("channel");
        String ver = q.get("version");
        if (art == null || ch == null || ver == null || art.isBlank() || ch.isBlank() || ver.isBlank()) {
            HttpUtils.sendText(exchange, 400, "artifact, channel, and version required");
            return;
        }
        Artifact artifact = new Artifact(art.toUpperCase());
        Channel channel = Channel.valueOf(ch.toUpperCase());
        Version version = Version.parse(ver);
        Path jar = releases.resolveBaseJar(artifact, channel, version);
        if (!Files.isRegularFile(jar)) {
            HttpUtils.sendText(exchange, 404, "jar not found");
            return;
        }
        long len = Files.size(jar);
        exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
        exchange.sendResponseHeaders(200, len);
        try (var in = Files.newInputStream(jar);
             var os = exchange.getResponseBody()) {
            in.transferTo(os);
        }
    }
}
