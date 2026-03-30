package dev.sweeyu.microsoft.auth;

import java.net.http.HttpRequest;

public final class HttpUtils {

    public static HttpRequest.Builder http() {
        return HttpRequest.newBuilder().header("user-agent", "sweety/1.0");
    }

    private HttpUtils() {}
}
