package dev.sweety.network;

import lombok.experimental.UtilityClass;
import java.net.http.HttpRequest;

@UtilityClass
public class HttpUtils {

    public HttpRequest.Builder http() {
        return HttpRequest.newBuilder().header("user-agent", "ecstacy/1.0");
    }

}
