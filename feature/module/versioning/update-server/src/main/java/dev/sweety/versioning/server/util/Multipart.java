package dev.sweety.versioning.server.util;

import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Multipart {

    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, byte[]> files = new HashMap<>();

    public String getField(String name) {
        return fields.get(name);
    }

    public byte[] getFile(String name) {
        return files.get(name);
    }

    public static Multipart parse(HttpExchange exchange, byte[] body) {

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

        if (contentType == null || !contentType.contains("multipart/form-data")) {
            throw new IllegalArgumentException("Not multipart");
        }

        String boundary = contentType.split("boundary=")[1];

        String raw = new String(body, StandardCharsets.ISO_8859_1);
        String[] parts = raw.split("--" + boundary);

        Multipart form = new Multipart();

        for (String part : parts) {

            if (!part.contains("Content-Disposition")) continue;

            String[] sections = part.split("\r\n\r\n", 2);
            if (sections.length < 2) continue;

            String headers = sections[0];
            String data = sections[1].trim();

            String name = HttpUtils.extractName(headers);
            String filename = HttpUtils.extractFilename(headers);

            if (filename == null) {
                form.fields.put(name, data.trim());
            } else {
                form.files.put(name, data.getBytes(StandardCharsets.ISO_8859_1));
            }

        }

        return form;

    }

}