package dev.sweety.patch.diff;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.archive.JavaArchive;
import dev.sweety.patch.archive.MapArchive;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.DeleteOperation;
import dev.sweety.patch.model.ModifyOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class PatchDiffEngineTest {

    @Test
    void testDiffOperations() {
        PatchDiffEngine engine = new PatchDiffEngine(new Sha256Hash(), null);

        Map<String, byte[]> oldEntries = new TreeMap<>();
        oldEntries.put("stay.txt", "stay".getBytes());
        oldEntries.put("stay.bin", new byte[]{0x01, 0x02});
        oldEntries.put("change.txt", "old".getBytes());
        oldEntries.put("change.bin", new byte[]{0x02, 0x04});
        oldEntries.put("delete.txt", "gone".getBytes());
        oldEntries.put("delete.bin", new byte[]{0x01, 0x03, 0x04});

        Map<String, byte[]> newEntries = new TreeMap<>();
        newEntries.put("stay.txt", "stay".getBytes());
        newEntries.put("stay.bin", new byte[]{0x01, 0x02});
        newEntries.put("change.txt", "new".getBytes());
        newEntries.put("change.bin", new byte[]{0x01, 0x03, 0x04});
        newEntries.put("add.txt", "hello".getBytes());
        newEntries.put("add.bin", new byte[]{0x01, 0x03, 0x04, 0x12});

        MapArchive oldArch = new MapArchive(oldEntries);
        MapArchive newArch = new MapArchive(newEntries);

        Patch patch = engine.diff(oldArch, newArch, "1.0", "1.1");

        List<PatchOperation> ops = patch.getOperations();
        // 2 adds, 2 deletes, 2 modifies = 6 total
        assertEquals(6, ops.size());

        // ADD checks
        assertTrue(ops.stream().anyMatch(o -> o instanceof AddOperation && o.path().equals("add.txt") && Arrays.equals(o.data(), "hello".getBytes())));
        assertTrue(ops.stream().anyMatch(o -> o instanceof AddOperation && o.path().equals("add.bin") && Arrays.equals(o.data(), new byte[]{0x01, 0x03, 0x04, 0x12})));
        
        // DELETE checks
        assertTrue(ops.stream().anyMatch(o -> o instanceof DeleteOperation && o.path().equals("delete.txt")));
        assertTrue(ops.stream().anyMatch(o -> o instanceof DeleteOperation && o.path().equals("delete.bin")));
        
        // MODIFY checks
        assertTrue(ops.stream().anyMatch(o -> o instanceof ModifyOperation && o.path().equals("change.txt") && Arrays.equals(o.data(), "new".getBytes()) || (o.method() == PatchOperation.Method.TEXT_DIFF)));
        assertTrue(ops.stream().anyMatch(o -> o instanceof ModifyOperation && o.path().equals("change.bin") && Arrays.equals(o.data(), new byte[]{0x01, 0x03, 0x04})));
    }

    @Test
    void testErrorPaths() {
        assertThrows(NullPointerException.class, () -> new PatchDiffEngine(null, null));
        
        PatchDiffEngine engine = new PatchDiffEngine(new Sha256Hash(), null);
        Archive mock = new MapArchive(Collections.emptyMap());
        
        assertThrows(NullPointerException.class, () -> engine.diff(null, mock, "1", "2"));
        assertThrows(NullPointerException.class, () -> engine.diff(mock, null, "1", "2"));
        assertThrows(NullPointerException.class, () -> engine.diff(mock, mock, null, "2"));
        assertThrows(NullPointerException.class, () -> engine.diff(mock, mock, "1", null));
    }

    @Test
    void diff_javaArchive_matchesMapArchive(@TempDir Path dir) throws IOException {
        Map<String, byte[]> oldEntries = new TreeMap<>();
        oldEntries.put("keep.txt", "same".getBytes());
        oldEntries.put("chg.txt", "old".getBytes());
        oldEntries.put("gone.txt", "bye".getBytes());

        Map<String, byte[]> newEntries = new TreeMap<>();
        newEntries.put("keep.txt", "same".getBytes());
        newEntries.put("chg.txt", "new".getBytes());
        newEntries.put("add.txt", "+".getBytes());

        Path jOld = dir.resolve("old.jar");
        Path jNew = dir.resolve("new.jar");
        writeJar(jOld, oldEntries);
        writeJar(jNew, newEntries);

        PatchDiffEngine engine = new PatchDiffEngine(new Sha256Hash(), null);
        Patch expected = engine.diff(new MapArchive(oldEntries), new MapArchive(newEntries), "a", "b");
        try (JavaArchive a = new JavaArchive(jOld); JavaArchive b = new JavaArchive(jNew)) {
            Patch actual = engine.diff(a, b, "a", "b");
            assertEquals(expected.getOperations().size(), actual.getOperations().size());
            assertEquals(expected.getFromVersion(), actual.getFromVersion());
            assertEquals(expected.getToVersion(), actual.getToVersion());
            Set<String> expPaths = new TreeSet<>();
            expected.getOperations().forEach(o -> expPaths.add(o.type() + ":" + o.path()));
            Set<String> actPaths = new TreeSet<>();
            actual.getOperations().forEach(o -> actPaths.add(o.type() + ":" + o.path()));
            assertEquals(expPaths, actPaths);
        }
    }

    private static void writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
    }
}
