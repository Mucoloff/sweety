package dev.sweety.versioning.server.util.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import java.io.*;
import java.util.*;

public class Multipart {

    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, byte[]> files = new HashMap<>();

    public String getField(String name) {
        return fields.get(name);
    }

    public byte[] getFile(String name) {
        return files.get(name);
    }

    public static Multipart parse(HttpExchange exchange, byte[] body) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            throw new IllegalArgumentException("Not multipart");
        }

        String boundary = "--" + contentType.split("boundary=")[1];
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.ISO_8859_1);

        Multipart form = new Multipart();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(body);
             BufferedInputStream bis = new BufferedInputStream(bais)) {

            byte[] buffer = bis.readAllBytes();
            int pos = 0;

            while (pos < buffer.length) {
                // trova inizio boundary
                int start = indexOf(buffer, boundaryBytes, pos);
                if (start < 0) break;
                start += boundaryBytes.length + 2; // skip \r\n

                int end = indexOf(buffer, boundaryBytes, start);
                if (end < 0) end = buffer.length;

                byte[] part = Arrays.copyOfRange(buffer, start, end - 2); // -2 skip \r\n

                int headerEnd = indexOf(part, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
                if (headerEnd < 0) {
                    pos = end;
                    continue;
                }

                String headers = new String(part, 0, headerEnd, StandardCharsets.ISO_8859_1);
                byte[] dataBytes = Arrays.copyOfRange(part, headerEnd + 4, part.length);

                String name = HttpUtils.extractName(headers);
                String filename = HttpUtils.extractFilename(headers);

                if (filename == null) {
                    form.fields.put(name, new String(dataBytes, StandardCharsets.UTF_8).trim());
                } else {
                    form.files.put(name, dataBytes);
                }

                pos = end;
            }
        }

        return form;
    }

    private static int indexOf(byte[] array, byte[] target, int start) {
        for (int i = start; i <= array.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

}