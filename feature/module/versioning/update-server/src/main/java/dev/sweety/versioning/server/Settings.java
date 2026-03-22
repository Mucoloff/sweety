package dev.sweety.versioning.server;

public class Settings {

    private Settings() {
    }

    public static String ROLLBACK_TOKEN = "token";
    public static String WEBHOOK_SECRET = "secret";
    public static String TOKEN_GEN_SALT = "very-secret-key";

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
