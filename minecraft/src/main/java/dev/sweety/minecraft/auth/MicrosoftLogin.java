package dev.sweety.minecraft.auth;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.core.config.GsonUtils;
import dev.sweety.core.system.OperatingSystem;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class MicrosoftLogin {
    private static final String CLIENT_ID = "4673b348-3efa-4f6a-bbb6-34e141cdc638";
    private static final int PORT = 8080;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = GsonUtils.gson();

    public static CompletableFuture<String> getRefreshToken() {
        CompletableFuture<String> future = new CompletableFuture<>();

        BiConsumer<HttpServer, String> callback = (server, code) -> {
            future.complete(code);
            server.stop(5);
        };

        startServer(callback);

        OperatingSystem.detectOS()
                .open("https://login.live.com/oauth20_authorize.srf?client_id=" + CLIENT_ID
                        + "&response_type=code&redirect_uri=http://127.0.0.1:" + PORT
                        + "&scope=XboxLive.signin%20offline_access&prompt=select_account");

        return future;
    }

    private static <T> CompletableFuture<T> sendHttp(HttpRequest req, Class<T> responseType) {
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> gson.fromJson(body, responseType));
    }

    public static CompletableFuture<LoginData> login(String refreshToken) {
        HttpRequest req = HttpUtils.http().uri(URI.create("https://login.live.com/oauth20_token.srf"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=" + CLIENT_ID
                                + "&refresh_token=" + refreshToken
                                + "&grant_type=refresh_token"
                                + "&redirect_uri=http://127.0.0.1:" + PORT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        return sendHttp(req, AuthTokenResponse.class)
                .thenCompose(res -> {
                    if (res == null) return CompletableFuture.completedFuture(new LoginData());

                    String accessToken = res.access_token;
                    String newRefresh = res.refresh_token;

                    HttpRequest xblReq = HttpUtils.http().uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + accessToken +
                                            "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}"))
                            .header("Content-Type", "application/json")
                            .build();

                    return sendHttp(xblReq, XblXstsResponse.class)
                            .thenCompose(xblRes -> {
                                if (xblRes == null) return CompletableFuture.completedFuture(new LoginData());

                                HttpRequest xstsReq = HttpUtils.http().uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                                        .POST(HttpRequest.BodyPublishers.ofString("{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblRes.Token +
                                                "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}"))
                                        .header("Content-Type", "application/json")
                                        .build();

                                return sendHttp(xstsReq, XblXstsResponse.class)
                                        .thenCompose(xstsRes -> {
                                            if (xstsRes == null)
                                                return CompletableFuture.completedFuture(new LoginData());

                                            HttpRequest mcReq = HttpUtils.http().uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                                                    .POST(HttpRequest.BodyPublishers.ofString("{\"identityToken\":\"XBL3.0 x=" + xblRes.DisplayClaims.xui[0].uhs + ";" + xstsRes.Token + "\"}"))
                                                    .header("Content-Type", "application/json")
                                                    .build();

                                            return sendHttp(mcReq, McResponse.class)
                                                    .thenCompose(mcRes -> {
                                                        if (mcRes == null)
                                                            return CompletableFuture.completedFuture(new LoginData());

                                                        HttpRequest ownReq = HttpUtils.http().uri(URI.create("https://api.minecraftservices.com/entitlements/mcstore"))
                                                                .header("Authorization", "Bearer " + mcRes.access_token)
                                                                .build();

                                                        return sendHttp(ownReq, GameOwnershipResponse.class)
                                                                .thenCompose(ownRes -> {
                                                                    if (ownRes == null || !ownRes.hasGameOwnership())
                                                                        return CompletableFuture.completedFuture(new LoginData());

                                                                    HttpRequest profileReq = HttpUtils.http().uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                                                                            .header("Authorization", "Bearer " + mcRes.access_token)
                                                                            .build();

                                                                    return sendHttp(profileReq, ProfileResponse.class)
                                                                            .thenApply(profile -> {
                                                                                if (profile == null)
                                                                                    return new LoginData();
                                                                                return new LoginData(mcRes.access_token, newRefresh, profile.id, profile.name);
                                                                            });
                                                                });
                                                    });
                                        });
                            });
                });
    }

    @SneakyThrows
    private static void startServer(BiConsumer<HttpServer, String> codeCallback) {

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/", new Handler(server, codeCallback));
        server.start();
    }

    @ToString
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

            sendHttp(req, AuthTokenResponse.class)
                    .thenAccept(res -> codeCallback.accept(server, res == null ? null : res.refresh_token));
        }

        @SneakyThrows
        private void writeText(HttpExchange req, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            req.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = req.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private static class AuthTokenResponse {
        public String access_token;
        public String refresh_token;
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

    public static class GameOwnershipResponse {
        public Item[] items;

        private boolean hasGameOwnership() {
            if (items == null || items.length == 0) return false;
            boolean hasProduct = false, hasGame = false;
            for (Item item : items) {
                if ("product_minecraft".equals(item.name)) hasProduct = true;
                else if ("game_minecraft".equals(item.name)) hasGame = true;
            }
            return hasProduct && hasGame;
        }

        public static class Item {
            public String name;
        }
    }

    private static class ProfileResponse {
        public String id;
        public String name;
    }
}
