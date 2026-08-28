package dev.sweety.patch.archive;

import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JavaArchive implements Archive, AutoCloseable {

    private final Path path;
    private final PatchFilter filter;
    private final ClassNormalizer normalizer;
    private JarFile jar;

    public JavaArchive(Path path, PatchFilter filter, ClassNormalizer normalizer) {
        this.path = path;
        this.filter = filter;
        this.normalizer = normalizer;
    }

    public JavaArchive(Path path) {
        this(path, p -> false, null);
    }

    private JarFile jar() {
        if (jar == null) {
            if (!Files.exists(path)) throw new PatchException("Archive file does not exist: " + path.toAbsolutePath());
            try {
                jar = new JarFile(path.toFile());
            } catch (IOException e) {
                throw new PatchException("Failed to open JAR archive: " + path.toAbsolutePath(), e);
            }
        }
        return jar;
    }

    @Override
    public NavigableSet<String> entryNames() {
        TreeSet<String> set = new TreeSet<>();
        Enumeration<JarEntry> jarEntries = jar().entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry entry = jarEntries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (filter != null && filter.exclude(name)) {
                continue;
            }
            set.add(name);
        }
        return set;
    }

    @Override
    public byte[] readEntry(String path) {
        if (filter != null && filter.exclude(path)) {
            return null;
        }
        JarFile jf = jar();
        JarEntry entry = jf.getJarEntry(path);
        if (entry == null || entry.isDirectory()) return null;
        try (InputStream is = jf.getInputStream(entry)) {
            byte[] data = is.readAllBytes();
            if (path.endsWith(".class") && normalizer != null) {
                data = normalizer.normalize(data);
            }
            return data;
        } catch (IOException e) {
            throw new PatchException("Failed to read JAR entry: " + path, e);
        }
    }

    @Override
    public long uncompressedSize(String path) {
        if (filter != null && filter.exclude(path)) return -1;

        JarEntry entry = jar().getJarEntry(path);

        if (entry == null || entry.isDirectory()) return -1;
        long s = entry.getSize();
        return s >= 0 ? s : -1;
    }

    @Override
    public long crc32(String path) {
        if (filter != null && filter.exclude(path)) return -1;

        JarEntry entry = jar().getJarEntry(path);
        if (entry == null || entry.isDirectory()) return -1;

        long c = entry.getCrc();
        return c >= 0 ? c : -1;
    }

    @Override
    public void close() throws IOException {
        if (jar != null) {
            jar.close();
            jar = null;
        }
    }


}
