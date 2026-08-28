package dev.sweety.media;

import dev.sweety.util.logger.LoggerFactory;
import dev.sweety.util.logger.SimpleLogger;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Normalises the many shapes of {@code mpris:artUrl} into something loadable.
 *
 * <p>Chromium-based browsers hand out an extension-less temp file
 * ({@code file:///tmp/.org.chromium.Chromium.b6FK0I}) that is rewritten on every
 * track change; Spotify hands out an https URL; local players hand out a real
 * file path. All three end up here.
 */
public final class ArtworkResolver {

    private static final SimpleLogger LOG = LoggerFactory.getLogger(ArtworkResolver.class);

    private ArtworkResolver() {
    }

    /** @return a local path or an http(s) URL, or null when there is no usable artwork */
    public static String resolve(String artUrl) {
        if (artUrl == null || artUrl.isBlank()) {
            return null;
        }
        String trimmed = artUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("file://")) {
            try {
                Path path = Path.of(URI.create(trimmed));
                return Files.isReadable(path) ? path.toString() : null;
            } catch (Exception e) {
                LOG.debug("unreadable artUrl " + trimmed + ": " + e);
                return null;
            }
        }
        if (trimmed.startsWith("data:image")) {
            return trimmed;
        }
        Path path = Path.of(trimmed);
        return Files.isReadable(path) ? path.toString() : null;
    }
}
