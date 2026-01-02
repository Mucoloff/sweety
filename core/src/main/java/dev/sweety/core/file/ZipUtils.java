package dev.sweety.core.file;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@UtilityClass
public class ZipUtils {

    public final int ZIP_THRESHOLD = 64 * 1024; // 64 KB

    public byte[] fromFile(File file) throws IOException {
        if (file.isDirectory()) return zipDirectory(file);
        if (Files.size(file.toPath()) > ZIP_THRESHOLD) return zipFile(file);
        return Files.readAllBytes(file.toPath());
    }

    public byte[] zipFile(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            zos.putNextEntry(new ZipEntry(file.getName()));
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public byte[] zipDirectory(File dir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            Path basePath = dir.toPath();

            Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) throws IOException {
                    String entryName = basePath.relativize(d).toString().replace(File.separatorChar, '/') + "/";
                    if (!entryName.isEmpty() && !entryName.equals("/")) {
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                    String entryName = basePath.relativize(f).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (InputStream is = Files.newInputStream(f)) {
                        is.transferTo(zos);
                    }
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return baos.toByteArray();
    }

    @SneakyThrows
    public byte[] zipByteArray(byte[] data, String entryName) {
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

    @SneakyThrows
    public byte[] unzipFirstFileFromZip(byte[] zipData) {
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
    public File unzip(byte[] bytes, File outputDir) {
        if (!outputDir.exists() && !outputDir.mkdirs())
            throw new IOException("Cannot create output dir: " + outputDir);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            String base = outputDir.getCanonicalPath();

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Skip entry vuote o root '/'
                if (entryName == null || entryName.isBlank() || entryName.equals("/") || entryName.equals("\\")) {
                    zis.closeEntry();
                    continue;
                }

                File out = new File(outputDir, entryName);
                String canon = out.getCanonicalPath();

                // sicurezza: evita path traversal
                if (!canon.startsWith(base + File.separator))
                    throw new SecurityException("Invalid zip entry: " + entryName);

                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs())
                        throw new IOException("Failed to create dir: " + out);
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs())
                        throw new IOException("Failed to create parent dir: " + parent);

                    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(out.toPath()))) {
                        zis.transferTo(os);
                    }
                }

                zis.closeEntry();
            }
        }

        return outputDir;
    }
}
