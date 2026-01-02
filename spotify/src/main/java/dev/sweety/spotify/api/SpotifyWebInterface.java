package dev.sweety.spotify.api;

import dev.sweety.spotify.registry.EndpointRegistry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class SpotifyWebInterface {

    private static final HttpClient http = HttpClient.newHttpClient();

    public static CompletableFuture<String> request(String accessToken, EndpointRegistry.Type type, String url, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken);

        HttpRequest.Builder b = switch (type) {
            case GET -> builder.GET();
            case DELETE ->
                    body != null && !body.isEmpty() ? builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body)) : builder.DELETE();
            case POST ->
                    body != null && !body.isEmpty() ? builder.POST(HttpRequest.BodyPublishers.ofString(body)) : builder.POST(HttpRequest.BodyPublishers.noBody());
            case PUT ->
                    body != null && !body.isEmpty() ? builder.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)) : builder.PUT(HttpRequest.BodyPublishers.noBody());
        };

        HttpRequest request = b.build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return CompletableFuture.failedFuture(
                                new IOException("Unexpected status code: " + response.statusCode() + " body: " + response.body())
                        );
                    }
                    return CompletableFuture.completedFuture(response.body());
                });
    }

    public static CompletableFuture<String> request(String accessToken, EndpointRegistry endpointRegistry, String url, String body) {
        return request(accessToken, endpointRegistry.getType(), endpointRegistry.getUrl() + url, body);
    }
}
