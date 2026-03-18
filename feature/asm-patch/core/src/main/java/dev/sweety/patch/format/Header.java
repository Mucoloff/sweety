package dev.sweety.patch.format;

import com.google.gson.Gson;
import lombok.SneakyThrows;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

    public static final int ZIP_THRESHOLD = 256;

    public static byte[] zipByteArray(byte[] data, String entryName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream is = new ByteArrayInputStream(data)) {
                is.transferTo(zos);
            }
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static byte[] unzipFirstFileFromZip(byte[] zipData) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                zis.transferTo(baos);
                zis.closeEntry();
                return baos.toByteArray();
            }
        }
        throw new IOException("ZIP contains no file entries");
    }

    @SneakyThrows
    public static byte[] zipBytes(byte[] data, String entryName) {
        return zipByteArray(data, entryName);
    }

    @SneakyThrows
    public static byte[] unzipBytes(byte[] zipData) {
        return unzipFirstFileFromZip(zipData);
    }

}
