package dev.sweety.core.file;

import lombok.experimental.UtilityClass;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.zip.*;

@UtilityClass
public class ArchiveUtils {

    private final int ZIP_THRESHOLD = 64 * 1024; // 64 KB

    // ==================================================================================
    // GZIP OPERATIONS
    // ==================================================================================

    public byte[] compressGzip(byte[] data, byte[] signature) throws IOException {
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

    public byte[] decompressGzip(byte[] data, byte[] signature) throws IOException {
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

    public boolean isGzipCompressed(byte[] data, byte[] signature) {
        if (signature == null || signature.length == 0) return false;
        if (data.length < signature.length) return false;
        byte[] fileSignature = Arrays.copyOfRange(data, 0, signature.length);
        return Arrays.equals(fileSignature, signature);
    }

    // ==================================================================================
    // ZIP OPERATIONS
    // ==================================================================================

    public byte[] zipSmart(File file) throws IOException {
        if (file.isDirectory()) return zipDirectory(file);
        if (Files.size(file.toPath()) > ZIP_THRESHOLD) return zipFile(file);
        return Files.readAllBytes(file.toPath());
    }

    public byte[] zipFile(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = createZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(file.getName()));
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public byte[] zipDirectory(File dir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = createZipOutputStream(baos)) {
            Path root = dir.toPath();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) throws IOException {
                    if (!d.equals(root)) {
                        String entryName = root.relativize(d).toString().replace(File.separatorChar, '/') + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                    String entryName = root.relativize(f).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(f, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return baos.toByteArray();
    }

    public byte[] zipBytes(byte[] data, String entryName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = createZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(data);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public byte[] unzipFirstFile(byte[] zipData) throws IOException {
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

    public File unzip(byte[] zipData, File outputDir) throws IOException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output dir: " + outputDir);
        }

        String targetDir = outputDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Skip invalid entries
                if (entryName.isBlank() || entryName.equals("/") || entryName.equals("\\")) {
                    zis.closeEntry();
                    continue;
                }
                
                // Security check for Zip Slip
                File targetFile = new File(outputDir, entryName);
                if (!targetFile.getCanonicalPath().startsWith(targetDir + File.separator)) {
                    throw new SecurityException("Invalid zip entry (Path Traversal attempt): " + entryName);
                }

                if (entry.isDirectory()) {
                    if (!targetFile.exists() && !targetFile.mkdirs()) {
                        throw new IOException("Failed to create dir: " + targetFile);
                    }
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Failed to create parent dir: " + parent);
                    }
                    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(targetFile.toPath()))) {
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

    private ZipOutputStream createZipOutputStream(OutputStream out) {
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out));
        zos.setLevel(Deflater.BEST_COMPRESSION);
        return zos;
    }
}

