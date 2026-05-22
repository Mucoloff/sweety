package dev.sweety.versioning.server;

/**
 * Runtime configuration loaded from {@code settings.json} (under storage) by {@link MainServer}, with selective env overrides.
 *
 * <table border="1" summary="Environment variables">
 *   <tr><th>Variable</th><th>Applies to</th><th>Notes</th></tr>
 *   <tr><td>{@code UPDATE_SERVER_ROOT}</td><td>{@link dev.sweety.versioning.server.adapter.out.storage.Storage}</td><td>Server filesystem root; default {@code storage}</td></tr>
 *   <tr><td>{@code NETTY_HANDSHAKE_SECRET}</td><td>this class, if absent from JSON</td><td>Must match launcher {@code SWEETY_HANDSHAKE_SECRET} for Netty HMAC</td></tr>
 *   <tr><td>{@code RELEASE_API_KEY}</td><td>this class, if absent from JSON</td><td>Protects {@code /release/base-jar} and {@code POST /release/download-token}; client: {@link dev.sweety.extension.versioning.RemoteReleaseSupport#ENV_RELEASE_API_KEY}</td></tr>
 *   <tr><td>{@code UPDATE_HTTP_BASE}</td><td>clients only</td><td>HTTP base URL for {@link dev.sweety.extension.versioning.RemoteReleaseSupport#fromEnvironment}; not read by server</td></tr>
 *   <tr><td>{@code SWEETY_HANDSHAKE_SECRET}</td><td>launcher client</td><td>Same value as {@code NETTY_HANDSHAKE_SECRET} on server when HMAC is enabled</td></tr>
 * </table>
 *
 * <p><b>Release HTTP</b>: {@code GET /release/latest} is public metadata. {@code GET /release/base-jar} streams the raw base JAR when {@link #RELEASE_API_KEY} is set.
 * {@code POST /release/download-token} mints a single-use token for {@code GET /download} (same path as the launcher); prefer this for parity with patched/tokenized downloads.
 */
public class Settings {

    private Settings() {
    }

    public static String ROLLBACK_TOKEN = "token";
    public static String WEBHOOK_SECRET = "secret";
    public static String TOKEN_GEN_SALT = "very-secret-key";

    /**
     * Shared secret for Netty handshake HMAC. When non-blank, {@link dev.sweety.versioning.server.api.netty.NettyHandshakeTrust}
     * requires a matching proof from the client. The launcher uses the same value via env {@code SWEETY_HANDSHAKE_SECRET}.
     */
    public static String NETTY_HANDSHAKE_SECRET = "";

    /**
     * When non-blank, {@code GET /release/base-jar} and {@code POST /release/download-token} require header {@code X-Sweety-Release-Key}.
     * Clients pass the same value via env {@link dev.sweety.extension.versioning.RemoteReleaseSupport#ENV_RELEASE_API_KEY} / JSON field {@code RELEASE_API_KEY}.
     */
    public static String RELEASE_API_KEY = "";

    public static float PERCENT_SIZE = 0.7f;
    public static int MAX_PATCH_VER_DISTANCE = 5;
    public static float DOWNLOAD_SPEED = 50 * 1024;
    public static long DEFAULT_TTL = 60 * 60 * 1000 * 1000L;

    public static long DOWNLOAD_EXPIRE_DELAY_MS = 30_000L;
    public static int MAX_CONCURRENT_DOWNLOADS = 50;

    public static int HISTORY_LIMIT = 20;

    public static int GLOBAL_RATE_LIMIT = 1000;
    public static int PER_IP_RATE_LIMIT = 100;
    public static long RATE_LIMIT_WINDOW = 60_000_000;

}
