package dev.sweety.core.crypt.signature;

import dev.sweety.core.crypt.ChecksumUtils;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.CRC32;

public class Signature {

    public static void applySignature(Path inputJar, String TARGET_CLASS, Consumer<Manifest> editManifest, Map<String, Object> fields, List<Watermark> watermarks, int watermarkSignature) throws Exception {
        try (FileSystem zfs = FileSystems.newFileSystem(inputJar, (ClassLoader) null)) {
            // MANIFEST
            Path manifestPath = zfs.getPath("META-INF/MANIFEST.MF");
            Manifest manifest;
            if (Files.exists(manifestPath)) {
                manifest = new Manifest(Files.newInputStream(manifestPath));
            } else {
                Path metaInf = zfs.getPath("META-INF");
                if (!Files.exists(metaInf)) {
                    Files.createDirectories(metaInf);
                }
                manifest = new Manifest();
            }
            if (editManifest != null) editManifest.accept(manifest);
            try (OutputStream os = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                manifest.write(os);
            }

            // Classe target
            String targetClassPathString = toClassResourcePathOrPassThrough(TARGET_CLASS);
            Path targetPath = zfs.getPath(targetClassPathString);
            if (Files.exists(targetPath)) {
                byte[] data = Files.readAllBytes(targetPath);
                byte[] patched = FieldInjector.inject(data, fields != null ? fields : Map.of());
                Files.write(targetPath, patched, StandardOpenOption.TRUNCATE_EXISTING);
            }

            // Watermarks
            List<Watermark> wm = watermarks == null ? List.of() : watermarks;
            for (Watermark watermark : wm) {
                String name = watermark.name();
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                byte[] data = watermark.data();

                CRC32 crc32 = ChecksumUtils.crc32(true);
                crc32.update(watermarkSignature);
                crc32.update(nameBytes);
                crc32.update(data);
                long crc = crc32.getValue();

                int size = MIN_SIZE + nameBytes.length + data.length;
                ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
                buffer.putInt(watermarkSignature);
                buffer.putInt(nameBytes.length);
                buffer.put(nameBytes);
                buffer.putInt(data.length);
                buffer.put(data);
                buffer.putLong(crc);

                byte[] array = buffer.array();

                UUID uuid = UUID.nameUUIDFromBytes(array);
                crc32.update(ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array());

                long idx = crc32.getValue();

                String fileName = genMixedFilename(
                        watermarkSignature,
                        nameBytes,
                        data
                );

                writeWatermark(
                        zfs,
                        PATHS[(int) (crc % PATHS.length)]
                                + PATHS[(int) (idx % PATHS.length)]
                                + fileName,
                        array
                );

            }
        }
    }

    private static final String[] PATHS = {
            "", ".cache/", "services/", "versions/", ".internal/"
    };

    private static final String[] LEGIT_NAMES = {
            "index", "refs", "objects", "module", "runtime", ".cache", "meta"
    };

    private static final String[] EXT = {
            "", ".dat", ".bin", ".idx"
    };

    private static String genMixedFilename(
            int watermarkSignature,
            byte[] nameBytes,
            byte[] data
    ) {
        int h = watermarkSignature;

        for (byte b : nameBytes) h = (h * 31) ^ b;
        for (byte b : data) h = (h * 17) ^ b;

        h = Math.abs(h);

        String base = LEGIT_NAMES[h % LEGIT_NAMES.length];

        int suffix = h & 0x1F;

        String ext = EXT[(h >>> 5) % EXT.length];

        if ((h & 0x7) < 2) return base + ext;

        return base + suffix + ext;
    }


    // watermarkSignature + nameBytes.length + data.length + crc
    private static final int MIN_SIZE = 4 + 4 + 4 + 8;

    public static Map<String, byte[]> testWatermarkResult(@NotNull Path inputJar, @NotNull List<String> watermarks, int watermarkSignature) throws Exception {
        final Map<String, byte[]> watermarkResults = new HashMap<>(watermarks.size());

        try (JarFile jar = new JarFile(inputJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory()) continue;
                if (!entry.getName().startsWith("META-INF/")) continue;
                if (entry.getSize() <= 0 || entry.getSize() > 1024) continue;

                try (InputStream in = jar.getInputStream(entry)) {
                    ByteBuffer buffer = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.BIG_ENDIAN);

                    if (buffer.remaining() < MIN_SIZE) continue;
                    if (buffer.getInt() != watermarkSignature) continue;

                    int nameLen = buffer.getInt();
                    if (nameLen <= 0 || nameLen > 256 || nameLen > buffer.remaining()) continue;

                    byte[] nameBytes = new byte[nameLen];
                    buffer.get(nameBytes);
                    String name = new String(nameBytes, StandardCharsets.UTF_8);

                    if (!watermarks.contains(name)) continue;

                    int dataLen = buffer.getInt();
                    if (dataLen <= 0 || dataLen > 512 || dataLen > buffer.remaining()) continue;

                    byte[] data = new byte[dataLen];
                    buffer.get(data);

                    if (buffer.remaining() < 8) continue;
                    long crc = buffer.getLong();

                    CRC32 crc32 = ChecksumUtils.crc32(true);
                    crc32.update(watermarkSignature);
                    crc32.update(nameBytes);
                    crc32.update(data);
                    if (crc != crc32.getValue()) continue;

                    watermarkResults.put(name, data);
                }
            }
        }

        return watermarkResults;
    }

    private static String toClassResourcePathOrPassThrough(String target) {
        if (target == null || target.isBlank()) return target;
        if (target.indexOf('/') >= 0) return target;
        String t = target.endsWith(".class") ? target.substring(0, target.length() - 6) : target;
        return t.replace('.', '/') + ".class";
    }

    private static void writeWatermark(FileSystem zfs, String name, byte @NotNull [] data) throws Exception {
        if (name == null || name.isBlank()) return;
        Path metaInf = zfs.getPath("META-INF");
        if (!Files.exists(metaInf)) {
            Files.createDirectories(metaInf);
        }
        Path wm = zfs.getPath("META-INF/" + name);
        Files.write(wm, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

}
