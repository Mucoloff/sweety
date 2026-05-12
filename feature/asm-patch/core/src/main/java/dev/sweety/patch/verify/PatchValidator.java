package dev.sweety.patch.verify;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.exception.PatchValidationException;
import dev.sweety.patch.format.archive.PatchArchiveConstants;
import dev.sweety.patch.format.archive.PatchArchiveIndex;
import dev.sweety.patch.format.archive.PatchArchiveOpEntry;
import dev.sweety.patch.format.archive.PatchArchiveReader;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

public class PatchValidator {

    private final HashFunction hashFunction;

    public PatchValidator(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
    }

    /**
     * Validates result archive contents against patch operations.
     * ADD/MODIFY require a non-blank expected hash per entry; DELETE entries must be absent in the result.
     */
    public void validate(Patch patch, Archive resultArchive) {
        NavigableSet<String> names = resultArchive.entryNames();

        for (PatchOperation op : patch.getOperations()) {
            String path = op.path();

            if (op.type() == PatchOperation.Type.DELETE) {
                if (names.contains(path)) {
                    throw new PatchValidationException("Validation failed: File " + path + " should be deleted but exists.");
                }
            } else {
                if (!names.contains(path)) {
                    throw new PatchValidationException("Validation failed: File " + path + " is missing.");
                }

                byte[] actualData = resultArchive.readEntry(path);
                requireNonEmptyExpectedHash(path, op.hash());
                String actualHash = hashFunction.calculateHash(actualData);

                if (!op.hash().equalsIgnoreCase(actualHash)) {
                    throw new PatchValidationException("Validation failed: Hash mismatch for " + path
                            + ". Expected: " + op.hash() + ", Actual: " + actualHash);
                }
            }
        }
    }

    /**
     * Validates an output JAR against a patch archive (index only + output bytes per path), without building an in-memory {@link Patch}.
     * <p>ADD/MODIFY operations must carry a non-blank expected hash. Invalid index header yields {@link PatchValidationException}.</p>
     */
    public void validatePatchArchive(ZipFile patchZip, Path outputJar) {
        final PatchArchiveIndex idx;
        try {
            idx = PatchArchiveReader.readIndex(patchZip);
        } catch (IOException e) {
            throw new PatchValidationException("Validation failed: could not read patch archive index", e);
        }
        if (!PatchArchiveConstants.HEADER.equals(idx.header)) {
            throw new PatchValidationException("Invalid patch archive header: expected " + PatchArchiveConstants.HEADER);
        }
        try (JarFile out = new JarFile(outputJar.toFile())) {
            for (PatchArchiveOpEntry e : idx.operations) {
                PatchOperation.Type t = PatchOperation.Type.valueOf(e.type.toUpperCase(Locale.ROOT));
                String path = e.path;
                if (t == PatchOperation.Type.DELETE) {
                    if (out.getJarEntry(path) != null) {
                        throw new PatchValidationException("Validation failed: File " + path + " should be deleted but exists.");
                    }
                } else {
                    JarEntry je = out.getJarEntry(path);
                    if (je == null) {
                        throw new PatchValidationException("Validation failed: File " + path + " is missing.");
                    }
                    requireNonEmptyExpectedHash(path, e.hash);
                    try (InputStream in = out.getInputStream(je)) {
                        byte[] data = in.readAllBytes();
                        String actualHash = hashFunction.calculateHash(data);
                        if (!e.hash.equalsIgnoreCase(actualHash)) {
                            throw new PatchValidationException("Validation failed: Hash mismatch for " + path
                                    + ". Expected: " + e.hash + ", Actual: " + actualHash);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new PatchValidationException("Validation failed: could not read output JAR", e);
        }
    }

    private static void requireNonEmptyExpectedHash(String path, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            throw new PatchValidationException(
                    "Validation requires a non-empty hash for ADD/MODIFY path: " + path);
        }
    }

}