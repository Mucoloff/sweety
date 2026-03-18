package dev.sweety.patch.archive;

import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarArchive implements Archive {

    private final File file;
    private final PatchFilter filter;
    private final ClassNormalizer normalizer;

    public JarArchive(File file, PatchFilter filter, ClassNormalizer normalizer) {
        this.file = file;
        this.filter = filter;
        this.normalizer = normalizer;
    }

    public JarArchive(File file) {
        this(file, path -> false, null);
    }

    @Override
    public Map<String, byte[]> entries() {
        Map<String, byte[]> entries = new TreeMap<>();

        if (!file.exists()) throw new RuntimeException("Archive file does not exist: " + file.getAbsolutePath());

        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> jarEntries = jar.entries();

            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();

                if (entry.isDirectory()) continue;

                String name = entry.getName();

                if (filter != null && filter.exclude(name)) continue;

                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] data = readAllBytes(is);

                    if (name.endsWith(".class") && normalizer != null) data = normalizer.normalize(data);

                    entries.put(name, data);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JAR archive: " + file.getAbsolutePath(), e);
        }

        return entries;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = is.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}