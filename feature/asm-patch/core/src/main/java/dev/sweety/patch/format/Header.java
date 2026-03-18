package dev.sweety.patch.format;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;

public enum Header {
    V1("ASM-PATCH-1"),

    ;

    public static final Gson GSON = new Gson().newBuilder().disableHtmlEscaping().create();

    private final String header;

    Header(String header) {
        this.header = header;
    }

    public String header() {
        return header;
    }

    public byte[] headerBytes() {
        return header.getBytes(StandardCharsets.UTF_8);
    }

}
