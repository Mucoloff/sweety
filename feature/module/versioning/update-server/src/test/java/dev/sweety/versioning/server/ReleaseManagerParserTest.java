package dev.sweety.versioning.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseManagerParserTest {

    private static final Gson GSON = new Gson().newBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Test
    void parsesGitHubReleasePayloadWithAssets() {
        String json = """
                {
                  "action": "published",
                  "release": {
                    "tag_name": "v1.2.3",
                    "assets": [
                      {
                        "name": "launcher-1.2.3.jar",
                        "browser_download_url": "https://example.test/launcher-1.2.3.jar"
                      },
                      {
                        "name": "app-1.2.3.jar",
                        "browser_download_url": "https://example.test/app-1.2.3.jar"
                      }
                    ]
                  }
                }
                """;

        JsonObject payload = GSON.fromJson(json, JsonObject.class);
        ReleaseManager.VersionState fallback = new ReleaseManager.VersionState("1.0.0", "1.0.0", Instant.now());

        ReleaseManager.ReleaseUpdate update = ReleaseManager.parseReleaseUpdate(payload, fallback);

        assertEquals("1.2.3", update.launcherVersion());
        assertEquals("1.2.3", update.appVersion());
        assertEquals("https://example.test/launcher-1.2.3.jar", update.launcherUrl());
        assertEquals("https://example.test/app-1.2.3.jar", update.appUrl());
    }
}

