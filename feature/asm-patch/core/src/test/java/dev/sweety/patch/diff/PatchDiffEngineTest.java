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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PatchDiffEngineTest {

    @Test
    void testDiffOperations() {
        PatchDiffEngine engine = new PatchDiffEngine(new Sha256Hash(), null);
        
        Map<String, byte[]> oldEntries = new HashMap<>();
        oldEntries.put("stay.txt", "stay".getBytes());
        oldEntries.put("change.txt", "old".getBytes());
        oldEntries.put("delete.txt", "gone".getBytes());
        
        Map<String, byte[]> newEntries = new HashMap<>();
        newEntries.put("stay.txt", "stay".getBytes());
        newEntries.put("change.txt", "new".getBytes());
        newEntries.put("add.txt", "hello".getBytes());

        Archive oldArch = () -> oldEntries;
        Archive newArch = () -> newEntries;

        Patch patch = engine.diff(oldArch, newArch, "1.0", "1.1");
        
        List<PatchOperation> ops = patch.getOperations();
        assertEquals(3, ops.size());
        
        assertTrue(ops.stream().anyMatch(o -> o instanceof AddOperation && o.path().equals("add.txt")));
        assertTrue(ops.stream().anyMatch(o -> o instanceof DeleteOperation && o.path().equals("delete.txt")));
        assertTrue(ops.stream().anyMatch(o -> o instanceof ModifyOperation && o.path().equals("change.txt")));
    }
}
