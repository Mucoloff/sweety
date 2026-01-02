package dev.sweety.cloud.packet.buffer;

import dev.sweety.core.file.ZipUtils;
import lombok.SneakyThrows;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public record FileBuffer(String fileName, boolean isDir, byte[] bytes) {

    private static final int ZIP_THRESHOLD = 64 * 1024; // 64 KB
    private static final String EXTENSION = ".buff.zip";

    // --- CREA UN FILEBUFFER DA FILE O DIRECTORY ---
    public static FileBuffer fromFile(File file) throws IOException {
        if (file.isDirectory()) return zipDirectory(file);
        if (Files.size(file.toPath()) > ZIP_THRESHOLD) return zipFile(file);
        return new FileBuffer(file.getName(), false, Files.readAllBytes(file.toPath()));
    }

    // --- ZIP SOLO UN FILE SINGOLO ---
    private static FileBuffer zipFile(File file) throws IOException {
        return new FileBuffer(file.getName() + EXTENSION, false, ZipUtils.zipFile(file));
    }

    // --- ZIP RICORSIVO PER DIRECTORY ---
    private static FileBuffer zipDirectory(File dir) throws IOException {
        return new FileBuffer(dir.getName() + EXTENSION, true, ZipUtils.zipDirectory(dir));
    }

    // --- LETTURA DA PACKETBUFFER ---
    public static FileBuffer read(PacketBuffer buffer) {
        String name = buffer.readString();
        boolean dir = buffer.readBoolean();
        byte[] data = buffer.readBytesArray();
        return new FileBuffer(name, dir, data);
    }

    // --- SCRITTURA SU PACKETBUFFER ---
    public void write(PacketBuffer buffer) {
        buffer.writeString(fileName);
        buffer.writeBoolean(isDir);
        buffer.writeBytesArray(bytes);
    }

    // --- UNZIP SICURO ---
    @SneakyThrows
    public File unzip(File outputDir) {
        return ZipUtils.unzip(bytes, outputDir);
    }

    // --- SALVA FILE BUFFER SU DISCO (GESTISCE ZIP AUTOMATICAMENTE) ---
    @SneakyThrows
    public File read(File directory) {
        if (!directory.exists()) Files.createDirectories(directory.toPath());

        if (fileName.endsWith(EXTENSION)) {
            File temp = new File(directory, fileName.replace(EXTENSION, ""));
            return unzip(temp);
        } else {
            File out = new File(directory, fileName);
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
                bos.write(bytes);
            }
            return out;
        }
    }

}
