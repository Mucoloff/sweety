package dev.sweety.patch.diff;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.DeleteOperation;
import dev.sweety.patch.model.ModifyOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

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

        Archive oldArch = () -> oldEntries;
        Archive newArch = () -> newEntries;

        Patch patch = engine.diff(oldArch, newArch, "1.0", "1.1");

        List<PatchOperation> ops = patch.getOperations();
        assertEquals(3, ops.size());

        assertTrue(ops.stream().anyMatch(o -> o instanceof AddOperation && o.path().equals("add.txt") && Arrays.equals(o.data(), "hello".getBytes())));
        assertTrue(ops.stream().anyMatch(o -> o instanceof AddOperation && o.path().equals("add.bin") && Arrays.equals(o.data(), new byte[]{0x01, 0x03, 0x04, 0x12})));
        assertTrue(ops.stream().anyMatch(o -> o instanceof DeleteOperation && o.path().equals("delete.txt")  && Arrays.equals(o.data(), "hello".getBytes())));
        assertTrue(ops.stream().anyMatch(o -> o instanceof ModifyOperation && o.path().equals("change.txt")  && Arrays.equals(o.data(), "new".getBytes())));
        assertTrue(ops.stream().anyMatch(o -> o instanceof ModifyOperation && o.path().equals("change.bin")  && Arrays.equals(o.data(), new byte[]{0x01, 0x03, 0x04})));
    }
}
