package dev.sweety.microsoft.auth;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dev.sweety.config.json.GsonUtils;
import dev.sweety.file.ResourceUtils;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.util.system.OperatingSystem;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Microsoft → Xbox Live → XSTS → Minecraft auth chain.
 *
 * <p>Two ways to obtain credentials:</p>
 * <ul>
 *   <li>{@link #refreshToken()} — auth-code loopback flow (opens browser, captures code on
 *       {@code 127.0.0.1:9675}); follow with {@link #login(String)}.</li>
 *   <li>{@link #loginDeviceCode(DeviceCodeCallback)} — device-code flow (show code + url, poll),
 *       runs the whole chain and returns {@link LoginData} directly.</li>
 * </ul>
 */
public class MicrosoftLogin {
    private static final SimpleLogger LOGGER = SimpleLogger.of(MicrosoftLogin.class);

    // Pre-approved (Mojang-allowlisted) public client ids, so login_with_xbox works without our own
    // app being reviewed. Meteor's id (4673b348-...) was de-listed by Microsoft → login_with_xbox now
    // returns HTTP 403 "Invalid app registration", so both flows use PrismLauncher's public/mobile id
    // (still allowlisted, device-flow enabled). Swap both to "c8c86c21-491f-41f8-855f-91c72b6b415c"
    // (LuceClient) once https://aka.ms/mce-reviewappid approves it.
    private static final String CLIENT_ID = "499c8d36-be2a-4231-9ebd-ef291b7bb64c";
    // Same public/mobile client for device code (Meteor's confidential/web id is invalid there:
    // AADSTS70002 "must be marked as 'mobile'").
    private static final String DEVICE_CLIENT_ID = "499c8d36-be2a-4231-9ebd-ef291b7bb64c";
    private static final int PORT = 8080;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = GsonUtils.gson();

    /** Callback used by the device-code flow to surface the user code + verification URL. */
    @FunctionalInterface
    public interface DeviceCodeCallback {
        void onCode(String userCode, String verificationUri, int expiresIn);
    }

    // ── auth-code loopback flow ───────────────────────────────────────────────

    /** Obtain a Microsoft refresh token by launching a local OAuth callback server and opening the browser. */
    public CompletableFuture<String> refreshToken() {
        CompletableFuture<String> future = new CompletableFuture<>();
        BiConsumer<HttpServer, String> callback = (server, code) -> {
            future.complete(code);
            server.stop(5);
        };
        startServer(callback);
        OperatingSystem.os()
                .open("https://login.live.com/oauth20_authorize.srf?client_id=" + CLIENT_ID
                        + "&response_type=code&redirect_uri=http://127.0.0.1:" + PORT
                        + "&scope=XboxLive.signin%20offline_access&prompt=select_account");
        return future;
    }

    // ── device-code flow ──────────────────────────────────────────────────────

    /**
     * Full device-code login: init → display code → poll → Xbox chain → profile.
     * Completes with {@link LoginData} ({@link LoginData#isBad()} on any failure/timeout).
     *
     * <p>Device-code tokens come from the Azure v2 endpoint, so this runs {@link #xboxChain}
     * directly with the polled access token rather than going through {@link #login}.</p>
     */
    public CompletableFuture<LoginData> loginDeviceCode(DeviceCodeCallback codeCallback) {
        CompletableFuture<LoginData> future = new CompletableFuture<>();

        HttpRequest req = HttpUtils.http()
                .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=" + DEVICE_CLIENT_ID + "&scope=XboxLive.signin%20offline_access"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        sendHttp(req, DeviceCodeResponse.class, "device-code-init").thenAccept(dcRes -> {
            if (dcRes == null || dcRes.device_code == null) {
                LOGGER.profile("device-code").error("init failed: {}",
                        dcRes == null ? "no response" : (dcRes.error + " / " + dcRes.error_description));
                future.complete(new LoginData());
                return;
            }
            String url = dcRes.verification_uri != null ? dcRes.verification_uri : dcRes.verification_url;
            LOGGER.profile("device-code").info("device code {} -> {}", dcRes.user_code, url);
            codeCallback.onCode(dcRes.user_code, url, dcRes.expires_in);
            pollDeviceCodeToken(dcRes.device_code, Math.max(1, dcRes.interval), future);
        });

        return future;
    }

    private void pollDeviceCodeToken(String deviceCode, int intervalSeconds,
                                     CompletableFuture<LoginData> future) {
        ScheduledExecutorService scheduler = ThreadUtil.singleThreadScheduler("luce-device-code-poll");

        scheduler.scheduleWithFixedDelay(() -> {
            HttpRequest req = HttpUtils.http()
                    .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "client_id=" + DEVICE_CLIENT_ID
                                    + "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                                    + "&device_code=" + deviceCode))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            sendHttp(req, AuthTokenResponse.class, "device-code-poll").thenAccept(res -> {
                if (res == null || res.error != null) {
                    String err = res == null ? null : res.error;
                    if (!"authorization_pending".equals(err)) {
                        LOGGER.profile("device-code").error("poll stopped: {}", err);
                        future.complete(new LoginData());
                        scheduler.shutdown();
                    }
                    // else still pending — keep polling
                    return;
                }
                scheduler.shutdown();
                xboxChain(res.access_token, res.refresh_token)
                        .thenAccept(future::complete)
                        .exceptionally(e -> { future.complete(new LoginData()); return null; });
            });
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    // ── token refresh + full MC-auth chain ────────────────────────────────────

    /** Refreshes the MSA token (auth-code flow), then runs the full Xbox→Minecraft chain. */
    public static CompletableFuture<LoginData> login(String refreshToken) {
        HttpRequest req = HttpUtils.http().uri(URI.create("https://login.live.com/oauth20_token.srf"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=" + CLIENT_ID
                                + "&refresh_token=" + refreshToken
                                + "&grant_type=refresh_token"
                                + "&redirect_uri=http://127.0.0.1:" + PORT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        return sendHttp(req, AuthTokenResponse.class, "token-refresh")
                .thenCompose(res -> {
                    if (res == null || res.access_token == null) {
                        LOGGER.profile("token-refresh").error("failed (no access_token)");
                        return CompletableFuture.completedFuture(new LoginData());
                    }
                    return xboxChain(res.access_token, res.refresh_token);
                });
    }

    /**
     * Refreshes a token originally issued by the device-code flow (DEVICE_CLIENT_ID).
     * Uses the v2 token endpoint instead of login.live.com, then runs the Xbox chain.
     */
    public static CompletableFuture<LoginData> loginDeviceCodeRefresh(String refreshToken) {
        HttpRequest req = HttpUtils.http()
                .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=" + DEVICE_CLIENT_ID
                                + "&refresh_token=" + refreshToken
                                + "&grant_type=refresh_token"
                                + "&scope=XboxLive.signin%20offline_access"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        return sendHttp(req, AuthTokenResponse.class, "token-refresh-device")
                .thenCompose(res -> {
                    if (res == null || res.access_token == null) {
                        LOGGER.profile("token-refresh-device").error("failed (no access_token)");
                        return CompletableFuture.completedFuture(new LoginData());
                    }
                    return xboxChain(res.access_token, res.refresh_token);
                });
    }

    /**
     * Runs Xbox Live → XSTS → Minecraft login → profile using an already-obtained MSA access token.
     *
     * @param msAccessToken a fresh MSA access token with the {@code XboxLive.signin} scope.
     * @param newRefresh    the refresh token to persist alongside the resulting account.
     */
    public static CompletableFuture<LoginData> xboxChain(String msAccessToken, String newRefresh) {
        HttpRequest xblReq = HttpUtils.http().uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + msAccessToken +
                                "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}"))
                .header("Content-Type", "application/json")
                .build();

        return sendHttp(xblReq, XblXstsResponse.class, "xbox-live")
                .thenCompose(xblRes -> {
                    if (xblRes == null || xblRes.Token == null) {
                        LOGGER.profile("xbox-live").error("auth failed");
                        return CompletableFuture.completedFuture(new LoginData());
                    }

                    HttpRequest xstsReq = HttpUtils.http().uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblRes.Token +
                                    "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}"))
                            .header("Content-Type", "application/json")
                            .build();

                    return sendHttp(xstsReq, XblXstsResponse.class, "xsts")
                            .thenCompose(xstsRes -> {
                                if (xstsRes == null || xstsRes.Token == null) {
                                    LOGGER.profile("xsts").error("auth failed");
                                    return CompletableFuture.completedFuture(new LoginData());
                                }

                                String uhs = xblRes.DisplayClaims != null
                                        && xblRes.DisplayClaims.xui != null
                                        && xblRes.DisplayClaims.xui.length > 0
                                        ? xblRes.DisplayClaims.xui[0].uhs : "";

                                HttpRequest mcReq = HttpUtils.http().uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                                        .POST(HttpRequest.BodyPublishers.ofString("{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsRes.Token + "\"}"))
                                        .header("Content-Type", "application/json")
                                        .build();

                                return sendHttp(mcReq, McResponse.class, "mc-login")
                                        .thenCompose(mcRes -> {
                                            if (mcRes == null || mcRes.access_token == null) {
                                                LOGGER.profile("mc-login").error("login_with_xbox failed");
                                                return CompletableFuture.completedFuture(new LoginData());
                                            }

                                            // Skip the entitlement (mcstore) check — it is unreliable for
                                            // migrated accounts; the profile fetch below 404s for non-owners.
                                            HttpRequest profileReq = HttpUtils.http().uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                                                    .header("Authorization", "Bearer " + mcRes.access_token)
                                                    .build();

                                            return sendHttp(profileReq, ProfileResponse.class, "mc-profile")
                                                    .thenApply(profile -> {
                                                        if (profile == null || profile.id == null) {
                                                            LOGGER.profile("mc-profile").error("fetch failed (account may not own Minecraft)");
                                                            return new LoginData();
                                                        }
                                                        LOGGER.profile("mc-profile").info("login OK as {} ({})", profile.name, profile.id);
                                                        return new LoginData(mcRes.access_token, newRefresh, profile.id, profile.name);
                                                    });
                                        });
                            });
                });
    }

    // ── internal helpers ──────────────────────────────────────────────────────

    private static <T> CompletableFuture<T> sendHttp(HttpRequest req, Class<T> responseType, String label) {
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .handle((resp, err) -> {
                    if (err != null) {
                        LOGGER.profile(label).error("request failed", err);
                        return null;
                    }
                    int sc = resp.statusCode();
                    String body = resp.body();
                    // device-code polling returns 400 authorization_pending until the user enters
                    // the code — expected, not an error, so don't spam the log with it.
                    boolean pending = body != null && body.contains("authorization_pending");
                    if ((sc < 200 || sc >= 300) && !pending) {
                        LOGGER.profile(label).warn("HTTP {}: {}", sc,
                                body != null && body.length() > 400 ? body.substring(0, 400) : body);
                    }
                    try {
                        return gson.fromJson(body, responseType);
                    } catch (Exception e) {
                        LOGGER.profile(label).error("JSON parse failed", e);
                        return null;
                    }
                });
    }

    private static void startServer(BiConsumer<HttpServer, String> codeCallback) {
        final HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start local server for authentication callback: " + e.getMessage(), e);
        }
        server.createContext("/", new Handler(server, codeCallback));
        server.start();
    }

    public static class LoginData {
        public String accessToken;
        public String newRefreshToken;
        public String uuid, username;

        public LoginData() {
        }

        public LoginData(String accessToken, String newRefreshToken, String uuid, String username) {
            this.accessToken = accessToken;
            this.newRefreshToken = newRefreshToken;
            this.uuid = uuid;
            this.username = username;
        }

        public boolean isBad() {
            return accessToken == null;
        }

        @Override
        public String toString() {
            return "LoginData{accessToken='%s', newRefreshToken='%s', uuid='%s', username='%s'}".formatted(accessToken, newRefreshToken, uuid, username);
        }
    }

    private record Handler(HttpServer server, BiConsumer<HttpServer, String> codeCallback) implements HttpHandler {

        @Override
        public void handle(HttpExchange req) {
            if (req.getRequestMethod().equals("GET")) {
                List<NameValuePair> query = URLEncodedUtils.parse(req.getRequestURI(), StandardCharsets.UTF_8);

                boolean ok = false;
                for (NameValuePair pair : query) {
                    if (pair.getName().equals("code")) {
                        handleCode(pair.getValue());
                        ok = true;
                        break;
                    }
                }

                String response = ResourceUtils.loadResource("callback/" + (ok ? "microsoft.html" : "fail.html"));
                writeText(req, response);
            }

        }

        private void handleCode(String code) {
            HttpRequest req = HttpUtils.http().uri(URI.create("https://login.live.com/oauth20_token.srf"))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "client_id=" + CLIENT_ID
                                    + "&code=" + code
                                    + "&grant_type=authorization_code"
                                    + "&redirect_uri=http://127.0.0.1:" + PORT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            sendHttp(req, AuthTokenResponse.class, "auth-code-exchange")
                    .thenAccept(res -> codeCallback.accept(server, res == null ? null : res.refresh_token));
        }


        private void writeText(HttpExchange req, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try {
                req.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = req.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (Exception e) {
                LOGGER.profile("callback").error("write failed", e);
            }

        }
    }

    private static class AuthTokenResponse {
        public String access_token;
        public String refresh_token;
        public String error;
    }

    private static class DeviceCodeResponse {
        public String device_code;
        public String user_code;
        public String verification_uri;
        public String verification_url;
        public int expires_in;
        public int interval;
        public String error;
        public String error_description;
    }

    private static class XblXstsResponse {
        public String Token;
        public DisplayClaims DisplayClaims;

        public static class DisplayClaims {
            public Claim[] xui;

            public static class Claim {
                public String uhs;

                public Claim(String uhs) {
                    this.uhs = uhs;
                }
            }
        }
    }

    private static class McResponse {
        public String access_token;
    }

    private static class ProfileResponse {
        public String id;
        public String name;
    }
}
