package dev.sweety.spotify;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.core.file.ResourceUtils;
import dev.sweety.core.logger.LogHelper;
import dev.sweety.core.system.OperatingSystem;
import dev.sweety.core.time.StopWatch;
import dev.sweety.core.util.ObjectUtils;
import dev.sweety.spotify.auth.AuthToken;
import dev.sweety.spotify.auth.SpotifyOAuth;
import dev.sweety.spotify.client.SpotifyClient;
import lombok.Getter;
import lombok.Setter;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SpotifyManager {

    private final int port;
    private final String redirectUrl;
    private final LogHelper logger;

    public SpotifyManager(String callback, int port, String clientId, String clientSecret, String redirectUrl, LogHelper logger) {
        this.port = port;
        this.redirectUrl = redirectUrl;
        this.logger = logger;
        this.oAuth = new SpotifyOAuth(callback, clientId, clientSecret);
    }

    private final SpotifyOAuth oAuth;
    private final SpotifyClient client = new SpotifyClient();

    private final StopWatch lastUpdate = new StopWatch();

    @Setter
    @Getter
    private String refreshToken;

    private int expire = 5 * 60 * 1000;

    public SpotifyClient client() {
        // If the last update was 5 minutes ago, we renew the token
        if (lastUpdate.hasPassed(expire) || !authenticated()) this.refreshAuth();
        return client;
    }

    public String setRefreshToken() {
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

                String response = ResourceUtils.loadResource(this.redirectUrl);

                byte[] bytes = response.getBytes();
                exchange.sendResponseHeaders(200, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }

                codeCallback.accept(server, code);
            });

            logger.info("Server listening...");
            server.start();
            isRunning.set(true);
        } catch (Exception e) {
            logger.error("exception caught: ", e);
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
                            logger.error("exception caught: ", e);
                            return null;
                        });

        startServer(callback);

        String url = this.oAuth.getAuthorizeUrl("user-read-playback-state user-read-email");

        try {
            OperatingSystem.detectOS().open(new URL(url));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return future;
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}
