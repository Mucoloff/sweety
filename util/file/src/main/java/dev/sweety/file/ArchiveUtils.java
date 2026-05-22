package dev.sweety.file;

import dev.sweety.data.buffer.BufferPool;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.zip.*;

public final class ArchiveUtils {

    private static final int ZIP_THRESHOLD = 64 * 1024; // 64 KB

    // ==================================================================================
    // GZIP OPERATIONS
    // ==================================================================================

    public static byte[] compressGzip(byte[] data, byte[] signature) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (signature != null && signature.length > 0) {
            baos.write(signature);
        }

        try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(data);
            gos.finish();
        }

        return baos.toByteArray();
    }

    public static byte[] decompressGzip(byte[] data, byte[] signature) throws IOException {
        int startOffset = 0;
        if (signature != null && signature.length > 0) {
            if (data.length < signature.length) {
                throw new IOException("Data too short for compressed format");
            }
            if (!Arrays.equals(data, 0, signature.length, signature, 0, signature.length)) {
                return data;
            }
            startOffset = signature.length;
        }

        try (InputStream is = new ByteArrayInputStream(data, startOffset, data.length - startOffset);
             GZIPInputStream gis = new GZIPInputStream(is)) {
            return gis.readAllBytes();
        }
    }

    public static boolean isGzipCompressed(byte[] data, byte[] signature) {
        if (signature == null || signature.length == 0) return false;
        if (data.length < signature.length) return false;
        return Arrays.equals(data, 0, signature.length, signature, 0, signature.length);
    }

    // ==================================================================================
    // ZIP OPERATIONS
    // ==================================================================================

    public static byte[] zipSmart(Path path) throws IOException {
        if (Files.isDirectory(path)) return zipDirectory(path);
        if (Files.size(path) > ZIP_THRESHOLD) return zipFile(path);
        return Files.readAllBytes(path);
    }

    public static byte[] zipFile(Path file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = createZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
            copyWithScratch(file, zos);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static byte[] zipDirectory(Path root) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = createZipOutputStream(baos)) {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path d, @NotNull BasicFileAttributes attrs) throws IOException {
                    if (!d.equals(root)) {
                        String entryName = root.relativize(d).toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path f, @NotNull BasicFileAttributes attrs) throws IOException {
                    String entryName = root.relativize(f).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    copyWithScratch(f, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return baos.toByteArray();
    }

    public static byte[] zipBytes(byte[] data, String entryName) throws IOException {
        return zipBytes(data, entryName, data.length);
    }

    /** Variant that treats only the first {@code length} bytes of {@code data} as the payload.
     *  Useful when {@code data} is a pooled (possibly oversized) scratch array. */
    public static byte[] zipBytes(byte[] data, String entryName, int length) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(length + 64);
        try (ZipOutputStream zos = createZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(data, 0, length);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static byte[] unzipFirstFile(byte[] zipData) throws IOException {
        return unzipFirstFile(zipData, zipData.length);
    }

    /** Variant that treats only the first {@code length} bytes of {@code zipData} as the ZIP stream.
     *  Useful when {@code zipData} is a pooled (possibly oversized) scratch array. */
    public static byte[] unzipFirstFile(byte[] zipData, int length) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData, 0, length))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                return zis.readAllBytes();
            }
        }
        throw new IOException("ZIP contains no file entries");
    }

    public static Path unzip(byte[] zipData, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path targetDir = outputDir.toAbsolutePath().normalize();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.isBlank() || entryName.equals("/") || entryName.equals("\\")) {
                    zis.closeEntry();
                    continue;
                }

                Path targetFile = outputDir.resolve(entryName).normalize();
                Path absTarget = targetFile.toAbsolutePath().normalize();

                if (!absTarget.startsWith(targetDir)) {
                    throw new SecurityException("Invalid zip entry (Path Traversal attempt): " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(absTarget);
                } else {
                    Path parent = absTarget.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(absTarget))) {
                        transferWithScratch(zis, os);
                    }
                }
                zis.closeEntry();
            }
        }
        return outputDir;
    }

    // ==================================================================================
    // INTERNAL HELPERS
    // ==================================================================================

    private static ZipOutputStream createZipOutputStream(OutputStream out) {
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out));
        zos.setLevel(Deflater.BEST_COMPRESSION);
        return zos;
    }

    private static void copyWithScratch(Path src, OutputStream dst) throws IOException {
        byte[] scratch = BufferPool.DEFAULT.borrowBytes(16384);
        try (InputStream is = Files.newInputStream(src)) {
            int n;
            while ((n = is.read(scratch)) > 0) dst.write(scratch, 0, n);
        } finally {
            BufferPool.DEFAULT.returnBytes(scratch);
        }
    }

    private static void transferWithScratch(InputStream src, OutputStream dst) throws IOException {
        byte[] scratch = BufferPool.DEFAULT.borrowBytes(16384);
        try {
            int n;
            while ((n = src.read(scratch)) > 0) dst.write(scratch, 0, n);
        } finally {
            BufferPool.DEFAULT.returnBytes(scratch);
        }
    }

    private ArchiveUtils(){}
}

