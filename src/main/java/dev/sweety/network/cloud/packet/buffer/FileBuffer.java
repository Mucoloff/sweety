package dev.sweety.network.cloud.packet.buffer;

import lombok.SneakyThrows;

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

public record FileBuffer(String fileName, boolean isDir, byte[] bytes) {

    private static final long ZIP_THRESHOLD = 64 * 1024; // 64 KB
    private static final String EXTENSION = ".buff.zip";

    // --- CREA UN FILEBUFFER DA FILE O DIRECTORY ---
    public static FileBuffer fromFile(File file) throws IOException {
        if (file.isDirectory()) {
            return zipDirectory(file);
        }

        long size = Files.size(file.toPath());
        byte[] data = Files.readAllBytes(file.toPath());

        // se è grande → comprimi
        if (size > ZIP_THRESHOLD) {
            return zipFile(file);
        }

        return new FileBuffer(file.getName(), false, data);
    }

    // --- ZIP SOLO UN FILE SINGOLO ---
    private static FileBuffer zipFile(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(baos))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            zos.putNextEntry(new ZipEntry(file.getName()));
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
        return new FileBuffer(file.getName() + EXTENSION, false, baos.toByteArray());
    }

    // --- ZIP RICORSIVO PER DIRECTORY ---
    private static FileBuffer zipDirectory(File dir) throws IOException {
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

        return new FileBuffer(dir.getName() + EXTENSION, true, baos.toByteArray());
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

    // --- NUOVI METODI: ZIPPARE/UNZIPPARE byte[] ---
    /**
     * Comprime un array di byte in un archivio ZIP con una sola entry.
     * Restituisce i byte dell'archivio ZIP.
     */
    @SneakyThrows
    public static byte[] zipByteArray(byte[] data, String entryName) {
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

    /**
     * Estrae il primo file non-directory trovato in un archivio ZIP fornito come byte[].
     * Restituisce i byte del file estratto. Lancia IOException se non ci sono entry di file.
     */
    @SneakyThrows
    public static byte[] unzipFirstFileFromZip(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
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

}
