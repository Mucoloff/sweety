package dev.sweety.patch.generator;

import dev.sweety.patch.applier.PatchApplier;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PatchGeneratorIntegrationTest {

    /** Include every entry — no exclusions. */
    private static final PatchFilter INCLUDE_ALL = path -> false;

    /** Identity normalizer — returns bytes unchanged. */
    private static final ClassNormalizer NORMALIZER_NONE = bytes -> bytes;

    @Test
    void generateAndApply_roundTrip(@TempDir Path tmp) throws Exception {
        // Use a .bin extension so the diff engine picks REPLACEMENT (not TEXT_DIFF)
        byte[] v1Content = "hello v1".getBytes(StandardCharsets.UTF_8);
        byte[] v2Content = "hello v2 patched".getBytes(StandardCharsets.UTF_8);

        // Build v1 and v2 jars
        Path v1Jar = tmp.resolve("v1.jar");
        Path v2Jar = tmp.resolve("v2.jar");
        writeJar(v1Jar, "data.bin", v1Content);
        writeJar(v2Jar, "data.bin", v2Content);

        // Generate patch v1 → v2
        Path patchDir = tmp.resolve("patches");
        Files.createDirectories(patchDir);

        var hashFunction = new Sha256Hash();
        PatchGenerator generator = new PatchGenerator(hashFunction, NORMALIZER_NONE, PatchTypes.PATCH_JAR);
        generator.generate(v1Jar, v2Jar, patchDir, "delta", "1.0", "2.0", INCLUDE_ALL);

        // Apply patch to v1, producing outJar
        Path outJar = tmp.resolve("out.jar");
        PatchApplier applier = new PatchApplier(PatchTypes.PATCH_JAR, hashFunction);
        applier.patch(v1Jar, outJar, patchDir, "delta");

        // Assert outJar contains the v2 content for data.bin
        try (JarFile jf = new JarFile(outJar.toFile())) {
            JarEntry entry = jf.getJarEntry("data.bin");
            try (var in = jf.getInputStream(entry)) {
                assertArrayEquals(v2Content, in.readAllBytes(),
                        "data.txt in patched jar must match v2 content exactly");
            }
        }
    }

    private static void writeJar(Path dest, String entryName, byte[] content) throws Exception {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(dest))) {
            JarEntry entry = new JarEntry(entryName);
            entry.setTime(0);
            jos.putNextEntry(entry);
            jos.write(content);
            jos.closeEntry();
        }
    }
}
