package dev.sweety.microsoft.auth;

import java.net.http.HttpRequest;

public final class HttpUtils {

    public static HttpRequest.Builder http() {
        return HttpRequest.newBuilder().header("user-agent", "luce/1.0");
    }

    private HttpUtils() {}
}
