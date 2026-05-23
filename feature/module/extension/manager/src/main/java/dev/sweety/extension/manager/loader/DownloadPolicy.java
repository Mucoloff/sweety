package dev.sweety.extension.manager.loader;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public record DownloadPolicy(
    Duration connectTimeout,
    Duration readTimeout,
    long maxBytes,
    Set<String> allowedSchemes,
    Optional<String> expectedSha256Hex
) {
    public static final DownloadPolicy DEFAULT = new DownloadPolicy(
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        50 * 1024 * 1024L, // 50 MB
        Set.of("https"),
        Optional.empty()
    );

    public static final DownloadPolicy ALLOW_HTTP = new DownloadPolicy(
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        50 * 1024 * 1024L,
        Set.of("https", "http"),
        Optional.empty()
    );
}
