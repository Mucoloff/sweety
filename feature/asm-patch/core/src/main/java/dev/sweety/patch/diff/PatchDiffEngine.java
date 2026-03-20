package dev.sweety.patch.diff;

import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.util.*;

public class PatchDiffEngine {

    private final HashFunction hashFunction;
    private final ClassNormalizer normalizer;

    public PatchDiffEngine(HashFunction hashFunction, ClassNormalizer classNormalizer) {
        this.hashFunction = hashFunction;
        this.normalizer = classNormalizer;
    }

    public Patch diff(Archive oldArchive, Archive newArchive, String fromVersion, String toVersion) {

        Map<String, byte[]> oldEntries = oldArchive.entries();
        Map<String, byte[]> newEntries = newArchive.entries();

        List<PatchOperation> ops = new ArrayList<>();
        // Use TreeSet for deterministic iteration order
        Set<String> allPaths = new TreeSet<>();
        allPaths.addAll(oldEntries.keySet());
        allPaths.addAll(newEntries.keySet());

        for (String path : allPaths) {
            byte[] oldData = oldEntries.get(path);
            byte[] newData = newEntries.get(path);

            if (oldData == null && newData != null) {
                // ADD
                ops.add(add(path, newData));
            } else if (oldData != null && newData == null) {
                // DELETE
                ops.add(delete(path));
            } else if (oldData != null) {
                // MODIFY
                if (shouldModify(path, oldData, newData)) {
                    ops.add(modify(path, newData));
                }
            }
        }

        return new Patch(fromVersion, toVersion, ops);
    }

    public ClassNormalizer normalizer() {
        return normalizer;
    }

    private boolean shouldModify(String path, byte[] oldData, byte[] newData) {
        if (Arrays.equals(oldData, newData)) return false;

        if (Arrays.equals(
                hashFunction.hash(oldData),
                hashFunction.hash(newData)
        )) return false;

        if (path.endsWith(".class") && normalizer != null) {
            byte[] normOld = normalizer.normalize(oldData);
            byte[] normNew = normalizer.normalize(newData);
            return !Arrays.equals(normOld, normNew);
        }

        return true;
    }

    private PatchOperation add(String path, byte[] data) {
        return PatchOperation.builder()
                .type(PatchOperation.Type.ADD)
                .path(path)
                .data(data)
                .hash(hashFunction.calculateHash(data))
                .build();
    }

    private PatchOperation modify(String path, byte[] data) {
        return PatchOperation.builder()
                .type(PatchOperation.Type.MODIFY)
                .path(path)
                .data(data)
                .hash(hashFunction.calculateHash(data))
                .build();
    }

    private PatchOperation delete(String path) {
        return PatchOperation.builder()
                .type(PatchOperation.Type.DELETE)
                .path(path)
                .data(null)
                .hash(null)
                .build();
    }

}