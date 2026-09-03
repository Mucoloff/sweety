package dev.sweety.patch.applier;

import dev.sweety.patch.exception.PatchException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Writes the patched output JAR atomically: streams entries to a temp file alongside the
 * final output path, then moves it into place with {@code ATOMIC_MOVE + REPLACE_EXISTING}.
 */
class JarWriter {

    /**
     * Writes all paths in {@code outputPaths} to {@code output}. For each path present in
     * {@code patchedBytes} the pre-resolved bytes are written; for all other paths the entry
     * is copied verbatim from {@code base}.
     */
    void write(JarFile base, PatchReadResult result, Path output) throws IOException {
        TreeSet<String> outputPaths = result.outputPaths();
        Map<String, byte[]> patchedBytes = result.patchedBytes();

        Path temp = output.resolveSibling(output.getFileName().toString() + ".tmp");
        try {
            try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                jos.setLevel(9);
                for (String path : outputPaths) {
                    byte[] data = patchedBytes.get(path);
                    if (data != null) writeJarEntry(jos, path, data);
                    else streamCopyEntry(base, path, jos);
                }
            }
            Files.move(temp, output, REPLACE_EXISTING, ATOMIC_MOVE);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                // Best-effort cleanup of temp jar
            }
        }
    }

    private void writeJarEntry(JarOutputStream jos, String path, byte[] data) throws IOException {
        JarEntry jarEntry = new JarEntry(path);
        jarEntry.setMethod(ZipEntry.DEFLATED);
        jarEntry.setTime(0);
        jos.putNextEntry(jarEntry);
        jos.write(data);
        jos.closeEntry();
    }

    private void streamCopyEntry(JarFile base, String path, JarOutputStream jos) throws IOException {
        JarEntry src = base.getJarEntry(path);
        if (src == null) throw new PatchException("Missing entry in base JAR: " + path);
        JarEntry dest = new JarEntry(path);
        dest.setMethod(ZipEntry.DEFLATED);
        dest.setTime(0);
        jos.putNextEntry(dest);
        try (InputStream in = base.getInputStream(src)) {
            in.transferTo(jos);
        }
        jos.closeEntry();
    }
}
