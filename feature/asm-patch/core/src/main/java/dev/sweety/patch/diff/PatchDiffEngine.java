package dev.sweety.patch.diff;

import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.util.*;

public class PatchDiffEngine {

    private final HashFunction hashFunction;
    private final ClassNormalizer classNormalizer;
    private final PatchFilter filter;

    public PatchDiffEngine(HashFunction hashFunction, ClassNormalizer classNormalizer, PatchFilter filter) {
        this.hashFunction = hashFunction;
        this.classNormalizer = classNormalizer;
        this.filter = filter;
    }

    public Patch diff(Archive oldArchive, Archive newArchive,
                      String fromVersion, String toVersion) {

        Map<String, byte[]> oldEntries = oldArchive.entries();
        Map<String, byte[]> newEntries = newArchive.entries();

        List<PatchOperation> ops = new ArrayList<>();
        // Use TreeSet for deterministic iteration order
        Set<String> allPaths = new TreeSet<>();
        allPaths.addAll(oldEntries.keySet());
        allPaths.addAll(newEntries.keySet());

        for (String path : allPaths) {
            if (filter.exclude(path)) continue;

            byte[] oldData = oldEntries.get(path);
            byte[] newData = newEntries.get(path);

            if (oldData == null && newData != null) {
                // ADD
                ops.add(add(path, newData));
            } else if (oldData != null && newData == null) {
                // DELETE
                ops.add(delete(path));
            } else if (oldData != null && newData != null) {
                // MODIFY
                if (shouldModify(path, oldData, newData)) {
                    ops.add(modify(path, newData));
                }
            }
        }

        return new Patch(fromVersion, toVersion, ops);
    }

    private boolean shouldModify(String path, byte[] oldData, byte[] newData) {
        // Optimization: Check raw equality first to avoid expensive normalization
        if (Arrays.equals(oldData, newData)) return false;

        // For .class files, normalize bytecode before hashing to ignore non-functional changes
        if (path.endsWith(".class")) {
            try {
                byte[] normOld = classNormalizer.normalize(oldData);
                byte[] normNew = classNormalizer.normalize(newData);

                return !Arrays.equals(
                        hashFunction.hash(normOld),
                        hashFunction.hash(normNew)
                );
            } catch (Exception e) {
                // Graceful fallback: if normalization fails, rely on raw comparison
                // Proceed to checks below
            }
        }

        // Default: raw comparison using hash function
        return !Arrays.equals(
                hashFunction.hash(oldData),
                hashFunction.hash(newData)
        );
    }

    private PatchOperation add(String path, byte[] data) {
        return PatchOperation.builder()
                .type(PatchOperation.Type.ADD)
                .path(path)
                .data(data)
                .hash(calculateHash(data))
                .build();
    }

    private PatchOperation modify(String path, byte[] data) {
        return PatchOperation.builder()
                .type(PatchOperation.Type.MODIFY)
                .path(path)
                .data(data)
                .hash(calculateHash(data))
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

    private String calculateHash(byte[] data) {
        if (data == null) return null;
        return bytesToHex(hashFunction.hash(data));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}