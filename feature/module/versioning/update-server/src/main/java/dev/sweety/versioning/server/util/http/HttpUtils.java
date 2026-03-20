package dev.sweety.versioning.server.util.http;

import com.sun.net.httpserver.HttpExchange;
import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpUtils {

    private HttpUtils() {}

    public static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return out;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx < 0) {
                out.put(urlDecode(pair), "");
            } else {
                out.put(urlDecode(pair.substring(0, idx)), urlDecode(pair.substring(idx + 1)));
            }
        }
        return out;
    }

    public static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static void sendJson(HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendText(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static boolean verifySignature(String secret, String signature, byte[] body) {

        if (secret == null || signature == null) return false;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            byte[] digest = mac.doFinal(body);

            String expected = toHex(digest);

            return constantEquals(expected, signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractName(String header) {

        int i = header.indexOf("name=\"");
        if (i < 0) return null;

        int start = i + 6;
        int end = header.indexOf('"', start);

        return header.substring(start, end);

    }


    public static String extractFilename(String header) {

        int i = header.indexOf("filename=\"");
        if (i < 0) return null;

        int start = i + 10;
        int end = header.indexOf('"', start);

        return header.substring(start, end);

    }

    private static boolean constantEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;

    }

}
