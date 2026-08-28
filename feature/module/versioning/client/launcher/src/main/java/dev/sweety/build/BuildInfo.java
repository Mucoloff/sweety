package dev.sweety.build;

public final class BuildInfo {

    public static final String BUILD_ID = "local-build";
    public static final String CHANNEL = "stable";
    public static final String CLIENT_ID = "unknown";
    public static final String VERSION = "0.0.0";
    public static final String TIMESTAMP = "0";

    // Per-user fields — injected by ASM (BuildInfoInjector) at download time
    public static final String LICENSE_KEY      = "__LICENSE_KEY__";
    public static final String LICENSE_KEY_HASH = "__LICENSE_KEY_HASH__";
    public static final String DISCORD_ID       = "__DISCORD_ID__";
    public static final String HANDSHAKE_SALT   = "__HANDSHAKE_SALT__";
    public static final String EDITION          = "__EDITION__";
    public static final String ISSUED_TO        = "__ISSUED_TO__";
    public static final String ISSUED_AT        = "__ISSUED_AT__";

    // Per-build fields — set at build time via Gradle (same value in every skeleton JAR for a given release)
    public static final String AUTH_SERVER_HOST  = "127.0.0.1";
    public static final String AUTH_SERVER_PORT  = "9901";
    public static final String MODULE_CHANNEL    = "stable";
    public static final String UPDATE_SERVER_URL = "http://localhost:8080";

    private BuildInfo() {

    }
}
