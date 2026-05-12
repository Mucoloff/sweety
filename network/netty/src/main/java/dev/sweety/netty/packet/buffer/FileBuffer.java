package dev.sweety.netty.packet.buffer;

import dev.sweety.file.ArchiveUtils;
import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.buffer.io.AbstractEncoder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public record FileBuffer(String fileName, boolean isDir, byte[] bytes) implements AbstractEncoder {

    private static final int ZIP_THRESHOLD = 64 * 1024; // 64 KB
    private static final String EXTENSION = ".buff.zip";

    public static FileBuffer fromFile(Path file) throws IOException {
        if (Files.isDirectory(file)) return zipDirectory(file);
        if (Files.size(file) > ZIP_THRESHOLD) return zipFile(file);
        return new FileBuffer(file.getFileName().toString(), false, Files.readAllBytes(file));
    }

    private static FileBuffer zipFile(Path file) throws IOException {
        return new FileBuffer(file.getFileName().toString() + EXTENSION, false, ArchiveUtils.zipFile(file));
    }

    private static FileBuffer zipDirectory(Path dir) throws IOException {
        return new FileBuffer(dir.getFileName().toString() + EXTENSION, true, ArchiveUtils.zipDirectory(dir));
    }

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

    public Path unzip(Path outputDir) {
        try {
            return ArchiveUtils.unzip(bytes, outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to unzip FileBuffer: " + fileName, e);
        }
    }

    public Path read(Path directory) {
        try {
            Files.createDirectories(directory);

            if (fileName.endsWith(EXTENSION)) {
                Path temp = directory.resolve(fileName.replace(EXTENSION, ""));
                Files.createDirectories(temp);
                return unzip(temp);
            }

            Path out = directory.resolve(fileName);
            try (OutputStream bos = new BufferedOutputStream(Files.newOutputStream(out))) {
                bos.write(bytes);
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write FileBuffer to disk: " + fileName, e);
        }
    }

}
