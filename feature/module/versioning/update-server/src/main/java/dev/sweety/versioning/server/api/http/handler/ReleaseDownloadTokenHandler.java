package dev.sweety.versioning.server.api.http.handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.logic.download.DownloadManager;
import dev.sweety.versioning.server.util.http.HttpUtils;
import dev.sweety.data.ObjectUtils;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * {@code JSON POST /release/download-token} — mints a one-time {@link dev.sweety.versioning.server.logic.download.token.Token}
 * for {@link dev.sweety.versioning.server.logic.download.DownloadHandler}. Requires {@link Settings#RELEASE_API_KEY} and header
 * {@code X-Sweety-Release-Key}.
 */
public final class ReleaseDownloadTokenHandler implements HttpHandler {

    private final DownloadManager downloadManager;
    private final IReleaseService releases;

    public ReleaseDownloadTokenHandler(DownloadManager downloadManager, IReleaseService releases) {
        this.downloadManager = downloadManager;
        this.releases = releases;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtils.sendText(exchange, 405, "Method not allowed");
            return;
        }
        if (Settings.RELEASE_API_KEY == null || Settings.RELEASE_API_KEY.isEmpty()) {
            HttpUtils.sendText(exchange, 403, "release token API disabled");
            return;
        }
        String headerKey = exchange.getRequestHeaders().getFirst("X-Sweety-Release-Key");
        if (headerKey == null || !HttpUtils.constantTimeEquals(headerKey, Settings.RELEASE_API_KEY)) {
            HttpUtils.sendText(exchange, 403, "forbidden");
            return;
        }
        byte[] raw;
        try {
            raw = exchange.getRequestBody().readAllBytes();
        } catch (IOException e) {
            HttpUtils.sendText(exchange, 400, "bad body");
            return;
        }
        JsonObject json;
        try {
            json = Utils.gson().fromJson(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            HttpUtils.sendText(exchange, 400, "invalid JSON");
            return;
        }
        if (json == null || !json.has("clientId") || !json.has("artifact") || !json.has("channel") || !json.has("version")) {
            HttpUtils.sendText(exchange, 400, "clientId, artifact, channel, version required");
            return;
        }
        UUID clientId;
        try {
            clientId = ObjectUtils.parseUuid(json.get("clientId").getAsString());
        } catch (IllegalArgumentException e) {
            HttpUtils.sendText(exchange, 400, "invalid clientId");
            return;
        }
        Artifact artifact = new Artifact(json.get("artifact").getAsString().toUpperCase());
        Channel channel = Channel.valueOf(json.get("channel").getAsString().toUpperCase());
        Version version = Version.parse(json.get("version").getAsString());
        Version from = json.has("from") && !json.get("from").isJsonNull()
                ? Version.parse(json.get("from").getAsString())
                : Version.ZERO;
        DownloadType downloadType = json.has("downloadType") && !json.get("downloadType").isJsonNull()
                ? DownloadType.valueOf(json.get("downloadType").getAsString().toUpperCase())
                : DownloadType.FULL;

        Path jar;
        try {
            jar = releases.resolveBaseJar(artifact, channel, version);
        } catch (IOException e) {
            HttpUtils.sendText(exchange, 500, "resolve error");
            return;
        }
        if (!Files.isRegularFile(jar)) {
            HttpUtils.sendText(exchange, 404, "jar not found");
            return;
        }

        String tokenStr = downloadManager.generate(clientId, artifact, channel, version, from, downloadType);
        String q = "clientId=" + URLEncoder.encode(clientId.toString(), StandardCharsets.UTF_8)
                + "&token=" + URLEncoder.encode(tokenStr, StandardCharsets.UTF_8);
        String downloadPath = "/download?" + q;

        JsonObject out = new JsonObject();
        out.addProperty("token", tokenStr);
        out.addProperty("downloadPath", downloadPath);
        HttpUtils.sendJson(exchange, Utils.gson().toJson(out));
    }
}
