package dev.sweety.patch.format.archive;

import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.type.PatchTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatchArchiveMergerTest {

    @Test
    void merge_concatenatesOperationsAndRenumbersPayload(@TempDir Path dir) throws Exception {
        Sha256Hash hf = new Sha256Hash();
        byte[] da = "A".getBytes(StandardCharsets.UTF_8);
        byte[] db = "B".getBytes(StandardCharsets.UTF_8);
        Patch seg = new Patch("1.0", "2.0", List.of(
                new AddOperation("a.txt", hf.calculateHash(da), da)));
        Patch tail = new Patch("1.0", "2.0", List.of(
                new AddOperation("b.txt", hf.calculateHash(db), db)));

        Path pSeg = dir.resolve("seg.patch.jar");
        Path pTail = dir.resolve("tail.patch.jar");
        Path out = dir.resolve("merged.patch.jar");

        try (OutputStream os = Files.newOutputStream(pSeg)) {
            PatchTypes.PATCH_JAR.writer().write(seg, os);
        }
        try (OutputStream os = Files.newOutputStream(pTail)) {
            PatchTypes.PATCH_JAR.writer().write(tail, os);
        }

        PatchArchiveMerger.merge(pSeg, pTail, out);

        Patch merged = PatchTypes.PATCH_JAR.reader().read(new ByteArrayInputStream(Files.readAllBytes(out)));
        assertEquals(2, merged.getOperations().size());
        assertEquals("a.txt", merged.getOperations().get(0).path());
        assertEquals("b.txt", merged.getOperations().get(1).path());
        assertArrayEquals(da, merged.getOperations().get(0).data());
        assertArrayEquals(db, merged.getOperations().get(1).data());
    }
}
