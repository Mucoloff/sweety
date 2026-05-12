package dev.sweety.file;


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

            byte[] fileSignature = Arrays.copyOfRange(data, 0, signature.length);
            if (!Arrays.equals(fileSignature, signature)) {
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
        byte[] fileSignature = Arrays.copyOfRange(data, 0, signature.length);
        return Arrays.equals(fileSignature, signature);
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
            Files.copy(file, zos);
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
                    Files.copy(f, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return baos.toByteArray();
    }

    public static byte[] zipBytes(byte[] data, String entryName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = createZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(data);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static byte[] unzipFirstFile(byte[] zipData) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
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
                        zis.transferTo(os);
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

    private ArchiveUtils(){}
}

