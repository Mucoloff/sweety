package dev.sweety.netty.packet.buffer;

import dev.sweety.file.ArchiveUtils;
import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.buffer.io.AbstractEncoder;

import java.io.BufferedOutputStream;import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public record FileBuffer(String fileName, boolean isDir, byte[] bytes) implements AbstractEncoder {

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
        return new FileBuffer(file.getName() + EXTENSION, false, ArchiveUtils.zipFile(file));
    }

    // --- ZIP RICORSIVO PER DIRECTORY ---
    private static FileBuffer zipDirectory(File dir) throws IOException {
        return new FileBuffer(dir.getName() + EXTENSION, true, ArchiveUtils.zipDirectory(dir));
    }

    // --- LETTURA DA PACKETBUFFER ---
    public static FileBuffer read(final BufferReader buffer) {
        String name = buffer.readString();
        boolean dir = buffer.readBoolean();
        byte[] data = buffer.readByteArray();
        return new FileBuffer(name, dir, data);
    }

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writeString(fileName);
        buffer.writeBoolean(isDir);
        buffer.writeByteArray(bytes);
    }

    // --- UNZIP SICURO ---
    public File unzip(File outputDir) {
        try {
            return ArchiveUtils.unzip(bytes, outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to unzip FileBuffer: " + fileName, e);
        }
    }

    // --- SALVA FILE BUFFER SU DISCO (GESTISCE ZIP AUTOMATICAMENTE) ---
    public File read(File directory) {
        if (!directory.exists()) {
            try {
                Files.createDirectories(directory.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory: " + directory.getAbsolutePath(), e);
            }
        }

        if (fileName.endsWith(EXTENSION)) {
            File temp = new File(directory, fileName.replace(EXTENSION, ""));
            return unzip(temp);
        } else {
            File out = new File(directory, fileName);
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
                bos.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write FileBuffer to disk: " + out.getAbsolutePath(), e);
            }
            return out;
        }
    }

}
