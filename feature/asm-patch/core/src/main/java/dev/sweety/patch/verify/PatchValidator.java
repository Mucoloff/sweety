package dev.sweety.patch.verify;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.util.Map;

public class PatchValidator {

    private final HashFunction hashFunction;

    public PatchValidator(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
    }

    public void validate(Patch patch, Archive resultArchive) {
        Map<String, byte[]> entries = resultArchive.entries();

        for (PatchOperation op : patch.getOperations()) {
            String path = op.getPath();

            if (op.getType() == PatchOperation.Type.DELETE) {
                if (entries.containsKey(path)) {
                    throw new RuntimeException("Validation failed: File " + path + " should be deleted but exists.");
                }
            } else {
                // ADD or MODIFY
                if (!entries.containsKey(path)) {
                    throw new RuntimeException("Validation failed: File " + path + " is missing.");
                }

                byte[] actualData = entries.get(path);
                String expectedHash = op.getHash();
                String actualHash = hashFunction.calculateHash(actualData);

                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    throw new RuntimeException("Validation failed: Hash mismatch for " + path
                            + ". Expected: " + expectedHash + ", Actual: " + actualHash);
                }
            }
        }
    }

}