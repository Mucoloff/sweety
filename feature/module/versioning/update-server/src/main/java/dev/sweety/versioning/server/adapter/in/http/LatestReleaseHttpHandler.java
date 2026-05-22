package dev.sweety.versioning.server.adapter.in.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import dev.sweety.versioning.server.util.http.HttpUtils;

import java.io.IOException;
import java.util.Map;

/** {@code GET /release/latest?artifact=APP&channel=STABLE} — public metadata. */
public final class LatestReleaseHttpHandler implements HttpHandler {

    private final IReleaseService releases;

    public LatestReleaseHttpHandler(IReleaseService releases) {
        this.releases = releases;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtils.sendText(exchange, 405, "Method not allowed");
            return;
        }
        Map<String, String> q = HttpUtils.parseQuery(exchange.getRequestURI().getRawQuery());
        String art = q.get("artifact");
        String ch = q.get("channel");
        if (art == null || ch == null || art.isBlank() || ch.isBlank()) {
            HttpUtils.sendText(exchange, 400, "artifact and channel required");
            return;
        }
        Artifact artifact = new Artifact(art.toUpperCase());
        Channel channel = Channel.valueOf(ch.toUpperCase());
        ReleaseInfo info = releases.latest(artifact, channel);
        if (info == null) {
            HttpUtils.sendText(exchange, 404, "not found");
            return;
        }
        HttpUtils.sendJson(exchange, Utils.gson().toJson(releaseInfoToJson(info)));
    }

    public static JsonObject releaseInfoToJson(ReleaseInfo info) {
        JsonObject o = new JsonObject();
        o.addProperty("version", info.version().toString());
        o.addProperty("channel", info.channel().prettyName());
        o.addProperty("updatedAt", info.updatedAt().toString());
        o.addProperty("rollout", info.rollout());
        return o;
    }
}
