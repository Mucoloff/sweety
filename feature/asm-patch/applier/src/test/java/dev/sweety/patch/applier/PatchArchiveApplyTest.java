package dev.sweety.patch.applier;

import dev.sweety.patch.exception.PatchFormatException;
import dev.sweety.patch.format.Header;
import dev.sweety.patch.format.archive.PatchArchiveConstants;
import dev.sweety.patch.format.archive.PatchArchiveIndex;
import dev.sweety.patch.format.archive.PatchArchiveOpEntry;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.DeleteOperation;
import dev.sweety.patch.model.ModifyOperation;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.type.PatchTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchArchiveApplyTest {

    @Test
    void applyPatchArchive_modifiesEntry(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(base))) {
            writeEntry(jos, "a.txt", "v1".getBytes(StandardCharsets.UTF_8));
        }

        var hf = new Sha256Hash();
        byte[] v2 = "v2".getBytes(StandardCharsets.UTF_8);
        String h = hf.calculateHash(v2);
        Patch patch = new Patch("1", "2", List.of(
                new ModifyOperation("a.txt", h, v2, PatchOperation.Method.REPLACEMENT)
        ));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PatchTypes.PATCH_JAR.writer().write(patch, bos);
        Path patchFile = dir.resolve("delta.patch.jar");
        Files.write(patchFile, bos.toByteArray());

        Path out = dir.resolve("out.jar");
        PatchApplier applier = new PatchApplier(PatchTypes.PATCH_JAR, hf);
        applier.applyPatchArchive(base, patchFile, out);

        try (JarFile jf = new JarFile(out.toFile())) {
            try (var in = jf.getInputStream(jf.getJarEntry("a.txt"))) {
                assertArrayEquals(v2, in.readAllBytes());
            }
        }
    }

    @Test
    void applyPatchArchive_deleteWithHash(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.jar");
        byte[] content = "delme".getBytes(StandardCharsets.UTF_8);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(base))) {
            writeEntry(jos, "x.txt", content);
        }

        var hf = new Sha256Hash();
        String contentHash = hf.calculateHash(content);
        Patch patch = new Patch("1", "2", List.of(new DeleteOperation("x.txt", contentHash)));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PatchTypes.PATCH_JAR.writer().write(patch, bos);
        Path patchFile = dir.resolve("delta.patch.jar");
        Files.write(patchFile, bos.toByteArray());

        Path out = dir.resolve("out.jar");
        PatchApplier applier = new PatchApplier(PatchTypes.PATCH_JAR, hf);
        applier.applyPatchArchive(base, patchFile, out);

        try (JarFile jf = new JarFile(out.toFile())) {
            assertTrue(jf.stream().noneMatch(e -> "x.txt".equals(e.getName())));
        }
    }

    @Test
    void applyPatchArchive_rejectsMaliciousPayloadRef(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(base))) {
            writeEntry(jos, "a.txt", "v1".getBytes(StandardCharsets.UTF_8));
        }

        var hf = new Sha256Hash();
        byte[] v2 = "v2".getBytes(StandardCharsets.UTF_8);
        String h = hf.calculateHash(v2);

        PatchArchiveIndex idx = new PatchArchiveIndex();
        idx.header = PatchArchiveConstants.HEADER;
        idx.fromVersion = "1";
        idx.toVersion = "2";
        PatchArchiveOpEntry op = new PatchArchiveOpEntry(PatchOperation.Type.MODIFY, "a.txt", h, PatchOperation.Method.REPLACEMENT);
        op.hash = (h);
        op.method = (PatchOperation.Method.REPLACEMENT);
        op.payloadEntry = ("../" + PatchArchiveConstants.PAYLOAD_PREFIX + "0");
        idx.operations = new ArrayList<>(List.of(op));

        Path badPatch = dir.resolve("bad.patch.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(badPatch))) {
            zos.putNextEntry(new ZipEntry(PatchArchiveConstants.INDEX_ENTRY));
            zos.write(Header.GSON.toJson(idx).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(PatchArchiveConstants.PAYLOAD_PREFIX + "0"));
            zos.write(v2);
            zos.closeEntry();
        }

        Path out = dir.resolve("out.jar");
        PatchApplier applier = new PatchApplier(PatchTypes.PATCH_JAR, hf);
        assertThrows(PatchFormatException.class, () -> applier.applyPatchArchive(base, badPatch, out));
    }

    @Test
    void applyPatchArchive_failureRemovesTempOutput(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(base))) {
            writeEntry(jos, "a.txt", "v1".getBytes(StandardCharsets.UTF_8));
        }

        Path sub = dir.resolve("sub");
        Files.createDirectories(sub);
        Path out = sub.resolve("out.jar");
        Path expectedTmp = sub.resolve("out.jar.tmp");

        PatchArchiveIndex idx = new PatchArchiveIndex();
        idx.header = PatchArchiveConstants.HEADER;
        idx.fromVersion = "1";
        idx.toVersion = "2";
        PatchArchiveOpEntry op = new PatchArchiveOpEntry(PatchOperation.Type.MODIFY, "a.txt", new Sha256Hash().calculateHash("x".getBytes(StandardCharsets.UTF_8)), PatchOperation.Method.REPLACEMENT);
        op.payloadEntry = (PatchArchiveConstants.PAYLOAD_PREFIX + "0");
        idx.operations = new ArrayList<>(List.of(op));

        Path badPatch = dir.resolve("missing-payload.patch.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(badPatch))) {
            zos.putNextEntry(new ZipEntry(PatchArchiveConstants.INDEX_ENTRY));
            zos.write(Header.GSON.toJson(idx).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        PatchApplier applier = new PatchApplier(PatchTypes.PATCH_JAR, new Sha256Hash());
        assertThrows(PatchFormatException.class, () -> applier.applyPatchArchive(base, badPatch, out));
        assertFalse(Files.exists(expectedTmp), "temporary output JAR should be removed after failure");
    }

    private static void writeEntry(JarOutputStream jos, String name, byte[] data) throws Exception {
        JarEntry e = new JarEntry(name);
        e.setTime(0);
        jos.putNextEntry(e);
        jos.write(data);
        jos.closeEntry();
    }
}
