package dev.sweety.spotify.auth;


import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import static dev.sweety.spotify.client.SpotifyClient.GSON;


public class SpotifyOAuth {

    private static final HttpClient http = HttpClient.newHttpClient();
    private final String redirectUrl;
    private final String clientId;
    private final String clientSecret;

    public SpotifyOAuth(String redirectUrl, String clientId, String clientSecret) {
        this.redirectUrl = redirectUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public CompletableFuture<AuthToken> getAccessToken(String code) {
        CompletableFuture<AuthToken> future = new CompletableFuture<>();

        String endpoint = "https://accounts.spotify.com/api/token";
        String encodedAuth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        String form = "grant_type=authorization_code" +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        sendRequest(request, future);

        return future;
    }

    public CompletableFuture<AuthToken> refreshAccessToken(String refreshToken) {
        CompletableFuture<AuthToken> future = new CompletableFuture<>();

        String endpoint = "https://accounts.spotify.com/api/token";
        String encodedAuth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        String form = "grant_type=refresh_token" +
                "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        sendRequest(request, future);

        return future;
    }

    public String getAuthorizeUrl(String scope) {
        return "https://accounts.spotify.com/authorize" +
                "?response_type=code" +
                "&client_id=" + clientId +
                "&redirect_uri=" + redirectUrl +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    private void sendRequest(HttpRequest request, CompletableFuture<AuthToken> future) {
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Unexpected status code: " + response.statusCode() + " body: " + response.body());
                    }
                    return response.body();
                })
                .thenAccept(body -> {
                    AuthToken token = GSON.fromJson(body, AuthToken.class);
                    if (token != null && token.getAccessToken() != null) {
                        future.complete(token);
                    } else {
                        future.completeExceptionally(new IOException("Access token not found in response: " + body));
                    }
                })
                .exceptionally(ex -> {
                    future.completeExceptionally(ex);
                    return null;
                });
    }

}
