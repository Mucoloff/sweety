package dev.sweety.util.signature;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.*;
import java.util.zip.CRC32C;

public class Signature {

    public static final String BUILD_INFO_CLASS = "dev/sweety/build/BuildInfo.class";
    public static final int WATERMARK_SIGNATURE = 0xDEADBEEF;
    private static final int MIN_SIZE = 4 + 4 + 4 + 8; // waterSig + nameLen + dataLen + crc

    private static final ThreadLocal<CRC32C> crc32ThreadLocal = ThreadLocal.withInitial(CRC32C::new);

    public static void applySignature(Path inputJar, String targetClass, Consumer<Manifest> editManifest, Map<String, Object> fields, List<Watermark> watermarks) throws IOException {
        applySignature(inputJar, targetClass, editManifest, fields, watermarks, WATERMARK_SIGNATURE);
    }
    
    // Legacy overload for backward compatibility if needed, but updated to call in-memory
    public static void applySignature(Path inputJar, String targetClass, Consumer<Manifest> editManifest, Map<String, Object> fields, List<Watermark> watermarks, int watermarkSignature) throws IOException {
        byte[] inputBytes = Files.readAllBytes(inputJar);
        byte[] patchedBytes = applySignatureInMemory(inputBytes, targetClass, editManifest, fields, watermarks, watermarkSignature);
        Files.write(inputJar, patchedBytes, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static byte[] applySignatureInMemory(byte[] inputJar,
                                                String targetClass,
                                                Consumer<Manifest> editManifest,
                                                Map<String, Object> fields,
                                                List<Watermark> watermarks,
                                                int watermarkSignature) throws IOException {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        Manifest manifest;

        // Read JAR
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(inputJar))) {
            manifest = jis.getManifest();
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] data = jis.readAllBytes();
                entries.put(entry.getName(), data);
            }
        }

        // Handle Manifest
        if (manifest == null) {
            manifest = new Manifest();
        }
        Attributes attrs = manifest.getMainAttributes();
        if (attrs.getValue(Attributes.Name.MANIFEST_VERSION) == null) {
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }
        if (editManifest != null) {
            editManifest.accept(manifest);
        }

        // Handle Target Class Injection
        String targetClassPath = toClassResourcePathOrPassThrough(targetClass);
        if (targetClassPath != null && entries.containsKey(targetClassPath)) {
            entries.compute(targetClassPath, (k, original) -> FieldInjector.inject(original, fields != null ? fields : Map.of()));
        }

        // Generate Watermarks using shared logic
        List<Watermark> wm = watermarks == null ? List.of() : watermarks;
        for (Watermark watermark : wm) {
            WatermarkEntry entry = generateWatermarkEntry(watermark, watermarkSignature);
            entries.put(entry.path, entry.data);
        }

        // Write JAR
        return writeJar(manifest, entries);
    }

    private static byte[] writeJar(Manifest manifest, Map<String, byte[]> entries) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             JarOutputStream jos = new JarOutputStream(baos)) {
            
            // Prioritize Manifest
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(manifestEntry);
            manifest.write(jos);
            jos.closeEntry();

            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(e.getKey())) {
                    continue;
                }
                JarEntry out = new JarEntry(e.getKey());
                jos.putNextEntry(out);
                jos.write(e.getValue());
                jos.closeEntry();
            }
            jos.finish();
            return baos.toByteArray();
        }
    }

    private record WatermarkEntry(String path, byte[] data) {
    }

    private static WatermarkEntry generateWatermarkEntry(Watermark watermark, int watermarkSignature) {
        String name = watermark.name();
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] data = watermark.data();

        // Checksum calculation
        long crc = calculateChecksum(watermarkSignature, nameBytes, data);

        // buffer construction
        int size = MIN_SIZE + nameBytes.length + data.length;
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(watermarkSignature);
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        buffer.putInt(data.length);
        buffer.put(data);
        buffer.putLong(crc);

        byte[] array = buffer.array();

        // Filename generation
        long idx = calculateUuidIndex(array);

        String fileName = genMixedFilename(watermarkSignature, nameBytes, data);
        
        // Path construction logic from original applySignatureInMemory
        String fullName = "META-INF/"
                + PATHS[(int) (crc % PATHS.length)]
                + PATHS[(int) (idx % PATHS.length)]
                + fileName;
        
        return new WatermarkEntry(fullName, array);
    }

    private static long calculateChecksum(int watermarkSignature, byte[] nameBytes, byte[] data) {
        final CRC32C crc32 = crc32ThreadLocal.get();
        crc32.reset();
        crc32.update(watermarkSignature);
        crc32.update(nameBytes);
        crc32.update(data);
        return crc32.getValue();
    }

    private static long calculateUuidIndex(byte[] data) {
        UUID uuid = UUID.nameUUIDFromBytes(data);
        final CRC32C crc32 = crc32ThreadLocal.get();
        crc32.reset();
        crc32.update(ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array());
        return crc32.getValue();
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

                    long calculatedCrc = calculateChecksum(watermarkSignature, nameBytes, data);
                    if (crc != calculatedCrc) continue;

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
