package dev.sweety.patch.applier;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.exception.PatchFormatException;
import dev.sweety.patch.exception.PatchValidationException;
import dev.sweety.patch.format.archive.PatchArchiveConstants;
import dev.sweety.patch.format.archive.PatchArchiveEntryNames;
import dev.sweety.patch.format.archive.PatchArchiveIndex;
import dev.sweety.patch.format.archive.PatchArchiveOpEntry;
import dev.sweety.patch.format.archive.PatchArchiveReader;
import dev.sweety.patch.hash.HashFunction;
import com.github.difflib.UnifiedDiffUtils;
import dev.sweety.patch.model.PatchOperation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads and validates a patch archive, extracts the index and op-entries, and resolves
 * the final byte content for each path that should appear in the output JAR.
 */
class PatchSourceReader {

    private final HashFunction hashFunction;

    PatchSourceReader(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
    }

    /**
     * Parses the patch archive and the base JAR, returning a {@link PatchReadResult} that
     * contains the ordered set of output paths and a map from path to resolved bytes for
     * patched entries.
     */
    PatchReadResult read(JarFile base, ZipFile patchArchive) throws IOException {
        PatchArchiveIndex idx = PatchArchiveReader.readIndex(patchArchive);
        if (!PatchArchiveConstants.HEADER.equals(idx.header))
            throw new PatchFormatException("Invalid patch archive header");

        verifyDeletePreconditions(base, idx);

        Map<String, PatchArchiveOpEntry> patchByPath = new HashMap<>();
        Set<String> needOriginal = new HashSet<>();
        for (PatchArchiveOpEntry e : idx.operations) {
            if (PatchOperation.Type.DELETE.equals(e.type)) continue;
            patchByPath.put(e.path, e);
            if (PatchOperation.Type.MODIFY.equals(e.type) && PatchOperation.Method.TEXT_DIFF.equals(e.method)) needOriginal.add(e.path);
        }

        Map<String, byte[]> originals = readOriginalsForPaths(base, needOriginal);
        TreeSet<String> outputPaths = buildOutputPaths(base, idx);

        Map<String, byte[]> patchedBytes = new LinkedHashMap<>();
        for (String path : outputPaths) {
            PatchArchiveOpEntry e = patchByPath.get(path);
            if (e != null && ( PatchOperation.Type.ADD.equals(e.type) ||  PatchOperation.Type.MODIFY.equals(e.type))) {
                byte[] payload = readZipPayload(patchArchive, e.payloadEntry);
                byte[] finalData = resolveEntryBytes(e, payload, originals);
                assertHash(finalData, e.hash);
                patchedBytes.put(path, finalData);
            }
        }

        return new PatchReadResult(outputPaths, patchedBytes);
    }

    private void verifyDeletePreconditions(JarFile base, PatchArchiveIndex idx) throws IOException {
        for (PatchArchiveOpEntry e : idx.operations) {
            if (!PatchOperation.Type.DELETE.equals(e.type)) continue;
            JarEntry je = base.getJarEntry(e.path);
            if (je == null) {
                throw new PatchException("Trying to delete non-existing file: " + e.path);
            }
            if (e.hash != null) {
                try (InputStream in = base.getInputStream(je)) {
                    byte[] existing = in.readAllBytes();
                    String actualHash = hashFunction.calculateHash(existing);
                    if (!e.hash.equalsIgnoreCase(actualHash)) {
                        throw new PatchValidationException(
                                "Delete precondition hash mismatch for " + e.path
                                        + ". Expected: " + e.hash + ", Actual: " + actualHash);
                    }
                }
            }
        }
    }

    private Map<String, byte[]> readOriginalsForPaths(JarFile base, Set<String> paths) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        for (String path : paths) {
            JarEntry je = base.getJarEntry(path);
            if (je == null) {
                throw new PatchException("Original file not found for modification: " + path);
            }
            try (InputStream in = base.getInputStream(je)) {
                map.put(path, in.readAllBytes());
            }
        }
        return map;
    }

    private TreeSet<String> buildOutputPaths(JarFile base, PatchArchiveIndex idx) {
        Set<String> deleted = new HashSet<>();
        for (PatchArchiveOpEntry e : idx.operations) {
            if (PatchOperation.Type.DELETE.equals(e.type)) {
                deleted.add(e.path);
            }
        }
        TreeSet<String> out = new TreeSet<>();
        Enumeration<JarEntry> en = base.entries();
        while (en.hasMoreElements()) {
            JarEntry je = en.nextElement();
            if (je.isDirectory()) {
                continue;
            }
            String name = je.getName();
            if (deleted.contains(name)) {
                continue;
            }
            out.add(name);
        }
        for (PatchArchiveOpEntry e : idx.operations) {
            if (PatchOperation.Type.ADD.equals(e.type)) {
                out.add(e.path);
            }
        }
        return out;
    }

    private byte[] resolveEntryBytes(PatchArchiveOpEntry e, byte[] payload, Map<String, byte[]> originals) {
        if (PatchOperation.Type.MODIFY.equals(e.type) && PatchOperation.Method.TEXT_DIFF.equals(e.method)) {
            byte[] originalData = originals.get(e.path);
            if (originalData == null) throw new PatchException("Original file not found for modification: " + e.path);
            return applyTextDiff(e.path, originalData, payload);
        }
        return payload;
    }

    private byte[] readZipPayload(ZipFile zf, String payloadEntry) throws IOException {
        ZipEntry ze = PatchArchiveEntryNames.requirePayloadZipEntry(zf, payloadEntry);
        try (InputStream in = zf.getInputStream(ze)) {
            return in.readAllBytes();
        }
    }

    private void assertHash(byte[] data, String expectedHash) {
        if (expectedHash == null) return;

        String calculatedHash = hashFunction.calculateHash(data);
        if (calculatedHash.equals(expectedHash)) return;

        throw new PatchValidationException("Patch integrity check failed"
                + ". Expected hash: " + expectedHash + ", Actual: " + calculatedHash);
    }

    private byte[] applyTextDiff(String path, byte[] originalData, byte[] diffData) {
        try {
            List<String> originalLines = toLines(originalData);
            List<String> diffLines = toLines(diffData);
            Patch<String> patchObj = UnifiedDiffUtils.parseUnifiedDiff(diffLines);
            List<String> patchedLines = DiffUtils.patch(originalLines, patchObj);

            StringJoiner joiner = new StringJoiner("\n", "", "\n");
            for (String patchedLine : patchedLines) joiner.add(patchedLine);
            return joiner.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PatchException("Failed to apply text diff for " + path, e);
        }
    }

    private List<String> toLines(byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        return Arrays.asList(content.split("\\r?\\n", -1));
    }
}
