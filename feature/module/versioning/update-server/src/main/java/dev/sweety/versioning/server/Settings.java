package dev.sweety.versioning.server;

public class Settings {

    private Settings() {}

    public static String ROLLBACK_TOKEN = "token";
    public static String WEBHOOK_SECRET = "secret";
    public static String TOKEN_GEN_SALT = "very-secret-key";

    public static float PERCENT_SIZE = 0.7f;
    public static int MAX_PATCH_VER_DISTANCE = 5;

}
