package dev.sweety.minecraft.auth;

import lombok.experimental.UtilityClass;
import java.net.http.HttpRequest;

@UtilityClass
public class HttpUtils {

    public HttpRequest.Builder http() {
        return HttpRequest.newBuilder().header("user-agent", "sweety/1.0");
    }

}
