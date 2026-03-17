package dev.sweety.patch.diff;

import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.util.Map;

public class PatchDiffEngine {

    private final HashFunction hashFunction;
    private final PatchFilter filter;

    public PatchDiffEngine(HashFunction hashFunction, PatchFilter filter) {
        this.hashFunction = hashFunction;
        this.filter = filter;
    }

    public Patch diff(Archive oldArchive, Archive newArchive,
                      String fromVersion, String toVersion) {

        Map<String, byte[]> oldEntries = oldArchive.entries();
        Map<String, byte[]> newEntries = newArchive.entries();

        List<PatchOperation> ops = new ArrayList<>();

        // ADD + MODIFY
        for (var entry : newEntries.entrySet()) {
            String path = entry.getKey();
            byte[] newData = entry.getValue();

            if (filter.exclude(path)) continue;

            byte[] oldData = oldEntries.get(path);

            if (oldData == null) {
                ops.add(add(path, newData));
            } else if (!Arrays.equals(
                    hashFunction.hash(oldData),
                    hashFunction.hash(newData))) {

                ops.add(modify(path, newData));
            }
        }

        // DELETE
        for (var entry : oldEntries.keySet()) {
            if (filter.exclude(entry)) continue;

            if (!newEntries.containsKey(entry)) {
                ops.add(delete(entry));
            }
        }

        return new Patch(fromVersion, toVersion, ops);
    }

    private PatchOperation add(String path, byte[] data) {

    }
    private PatchOperation modify(String path, byte[] data) {

    }
    private PatchOperation delete(String path) {

    }
}