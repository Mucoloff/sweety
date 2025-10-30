package dev.sweety.integration.spotify.client;

import com.google.gson.Gson;
import dev.sweety.integration.spotify.registry.EndpointRegistry;
import dev.sweety.integration.spotify.api.SpotifyWebInterface;
import dev.sweety.integration.spotify.model.Queue;
import dev.sweety.integration.spotify.model.device.Devices;
import dev.sweety.integration.spotify.model.playback.Playback;
import dev.sweety.integration.spotify.model.track.Track;
import dev.sweety.persistence.config.GsonUtils;

import java.util.concurrent.CompletableFuture;

public class SpotifyClient {

    public static final Gson GSON = GsonUtils.gson();

    private String accessToken;

    public SpotifyClient() {
    }

    public SpotifyClient(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public CompletableFuture<Track> getCurrentTrackAsync() {
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.CURRENTLY_PLAYING, "", "");

        return response.thenApply(s -> GSON.fromJson(s, Track.class));
    }

    public Track getCurrentTrack() {
        return getCurrentTrackAsync().join();
    }

    public CompletableFuture<Playback> getPlayBack() {
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.PLAYBACK, "", "");

        return response.thenApply(s -> GSON.fromJson(s, Playback.class));
    }

    public CompletableFuture<Devices> getDevices() {
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.DEVICES, "", "");

        return response.thenApply(s -> GSON.fromJson(s, Devices.class));
    }

    public CompletableFuture<Queue> getQueue() {
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.QUEUE, "", "");

        return response.thenApply(s -> GSON.fromJson(s, Queue.class));
    }

    public CompletableFuture<Boolean> play(String deviceId, String body) {
        String url = deviceId != null && !deviceId.isEmpty() ? ("?device_id=" + deviceId) : "";
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.PLAY, url, body);

        return response.thenApply(String::isEmpty);
    }

    public CompletableFuture<Boolean> pause(String deviceId) {
        String url = deviceId != null && !deviceId.isEmpty() ? ("?device_id=" + deviceId) : "";
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.PAUSE, url, "");

        return response.thenApply(String::isEmpty);
    }

    public CompletableFuture<Boolean> skipNext(String deviceId) {
        String url = deviceId != null && !deviceId.isEmpty() ? ("?device_id=" + deviceId) : "";
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.SKIP_NEXT, url, "");

        return response.thenApply(String::isEmpty);
    }

    public CompletableFuture<Boolean> skipPrevious(String deviceId) {
        String url = deviceId != null && !deviceId.isEmpty() ? ("?device_id=" + deviceId) : "";
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.SKIP_PREVIOUS, url, "");

        return response.thenApply(String::isEmpty);
    }

    public CompletableFuture<Boolean> addToQueue(String deviceId, String trackUri) {
        String url = deviceId != null && !deviceId.isEmpty() ? ("&device_id=" + deviceId) : "";
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.ADD_QUEUE, "?uri=" + trackUri.replace(":", "%3A") + url, "");

        return response.thenApply(String::isEmpty);
    }

    public CompletableFuture<Boolean> seekToTime(String deviceId, String position_ms) {
        String url = deviceId != null && !deviceId.isEmpty() ? ("&device_id=" + deviceId) : "";
        CompletableFuture<String> response = SpotifyWebInterface.request(accessToken, EndpointRegistry.SEEK, "?position_ms=" + position_ms + url, "");

        return response.thenApply(String::isEmpty);
    }
}
