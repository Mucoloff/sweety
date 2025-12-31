package dev.sweety.integration.spotify;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.core.time.StopWatch;
import dev.sweety.core.util.ObjectUtils;
import dev.sweety.core.util.ResourceUtils;
import dev.sweety.core.system.SystemUtils;
import dev.sweety.integration.spotify.client.SpotifyClient;
import dev.sweety.integration.spotify.auth.AuthToken;
import dev.sweety.integration.spotify.auth.SpotifyOAuth;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SpotifyManager {

    private final int port = 8000;
    private final String callback = "http://127.0.0.1:%s/callback".formatted(port);
    private final String clientId = "9f85c5aad1494db987852edda3a3a82e";
    private final String clientSecret = "3e68095b2dd4477cbd997ac9add3e70b";
    private final SpotifyOAuth oAuth = new SpotifyOAuth(callback, clientId, clientSecret);
    private final SpotifyClient client = new SpotifyClient();

    private final StopWatch lastUpdate = new StopWatch();

    private String refreshToken;

    private int expire = 5 * 60 * 1000;

    public SpotifyClient client() {
        // If the last update was 5 minutes ago, we renew the token
        if (lastUpdate.hasPassed(expire) || !authenticated()) this.refreshAuth();

        return client;
    }

    public void refreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public boolean authenticated() {
        return client.getAccessToken() != null;
    }

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public void startServer(BiConsumer<HttpServer, String> codeCallback) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = query.split("code=")[1];

                String response = ResourceUtils.loadResource("callback/spotify.html");

                byte[] bytes = response.getBytes();
                exchange.sendResponseHeaders(200, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }

                codeCallback.accept(server, code);
            });

            System.out.println("Server listening...");
            server.start();
            isRunning.set(true);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void refreshAuth() {
        this.lastUpdate.reset();

        Consumer<String> auth = token -> this.oAuth.refreshAccessToken(token).thenAccept(authToken -> {
            String refreshToken = authToken.getRefreshToken();

            if (!ObjectUtils.isNull(refreshToken)) this.refreshToken = refreshToken;

            this.client.setAccessToken(authToken.getAccessToken());
            this.lastUpdate.reset();
        });

        if (!ObjectUtils.isNull(this.refreshToken)) auth.accept(this.refreshToken);
        else doAuth().thenAccept(auth);
    }

    public CompletableFuture<String> doAuth() {

        CompletableFuture<String> future = new CompletableFuture<>();

        BiConsumer<HttpServer, String> callback = (server, code) ->
                this.oAuth.getAccessToken(code)
                        .thenAccept(token -> {
                            AuthToken newToken = this.oAuth.refreshAccessToken(token.getRefreshToken()).join();

                            if (!ObjectUtils.isNull(token.getRefreshToken())) this.refreshToken = token.getRefreshToken();

                            this.expire = newToken.getExpiresIn();
                            this.lastUpdate.reset();
                            this.client.setAccessToken(newToken.getAccessToken());

                            future.complete(newToken.getAccessToken());
                            server.stop(5);
                            isRunning.set(false);
                        })
                        .exceptionally(e -> {
                            e.printStackTrace(System.err);
                            return null;
                        });

        startServer(callback);

        String url = this.oAuth.getAuthorizeUrl("user-read-playback-state user-read-email");

        try {
            SystemUtils.detectOS().open(new URL(url));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return future;
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
