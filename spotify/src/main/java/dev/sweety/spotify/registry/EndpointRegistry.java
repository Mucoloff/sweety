package dev.sweety.spotify.registry;

import lombok.Getter;

public enum EndpointRegistry {

    DEVICES("/me/player/devices"),
    PLAYBACK("/me/player"),
    CURRENTLY_PLAYING("/me/player/currently-playing"),
    PLAY("/me/player/play", Type.PUT),
    PAUSE("/me/player/pause", Type.PUT),
    SEEK("/me/player/seek", Type.PUT),
    SKIP_NEXT("/me/player/next", Type.POST),
    SKIP_PREVIOUS("/me/player/previous", Type.POST),
    QUEUE("/me/player/queue"),
    ADD_QUEUE("/me/player/queue", Type.POST);

    public static final String BASE_URL = "https://api.spotify.com/v1";
    private final String endpoint;
    @Getter
    private final Type type;

    EndpointRegistry(String endpoint, Type type) {
        this.endpoint = BASE_URL + endpoint;
        this.type = type;
    }

    EndpointRegistry(String endpoint) {
        this(endpoint, Type.GET);
    }

    public String getUrl() {
        return endpoint;
    }

    public enum Type {
        GET, POST, PUT, DELETE
    }
}
