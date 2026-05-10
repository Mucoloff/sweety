package dev.sweety.patch.diff;

import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.DeleteOperation;
import dev.sweety.patch.model.ModifyOperation;
import org.jetbrains.annotations.NotNull;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PatchDiffEngine {

    private final HashFunction hashFunction;
    private final ClassNormalizer normalizer;

    public PatchDiffEngine(@NotNull HashFunction hashFunction, ClassNormalizer classNormalizer) {
        this.hashFunction = java.util.Objects.requireNonNull(hashFunction, "hashFunction cannot be null");
        this.normalizer = classNormalizer;
    }

    public Patch diff(@NotNull Archive oldArchive, @NotNull Archive newArchive, @NotNull String fromVersion, @NotNull String toVersion) {
        java.util.Objects.requireNonNull(oldArchive, "oldArchive cannot be null");
        java.util.Objects.requireNonNull(newArchive, "newArchive cannot be null");
        java.util.Objects.requireNonNull(fromVersion, "fromVersion cannot be null");
        java.util.Objects.requireNonNull(toVersion, "toVersion cannot be null");
        
        Map<String, byte[]> oldEntries = oldArchive.entries();
        Map<String, byte[]> newEntries = newArchive.entries();

        List<PatchOperation> ops = new ArrayList<>();
        Set<String> allPaths = new TreeSet<>();
        allPaths.addAll(oldEntries.keySet());
        allPaths.addAll(newEntries.keySet());

        for (String path : allPaths) {
            byte[] oldData = oldEntries.get(path);
            byte[] newData = newEntries.get(path);

            if (oldData == null && newData != null) {
                ops.add(add(path, newData));
            } else if (oldData != null && newData == null) {
                ops.add(delete(path));
            } else if (oldData != null) {
                if (shouldModify(path, oldData, newData)) {
                    ops.add(modify(path, oldData, newData));
                }
            }
        }

        return new Patch(fromVersion, toVersion, ops);
    }

    private boolean shouldModify(String path, byte[] oldData, byte[] newData) {
        if (Arrays.equals(oldData, newData)) return false;

        if (path.endsWith(".class") && normalizer != null) {
            byte[] normOld = normalizer.normalize(oldData);
            byte[] normNew = normalizer.normalize(newData);
            return !Arrays.equals(normOld, normNew);
        }

        return true;
    }

    private PatchOperation add(String path, byte[] data) {
        return new AddOperation(path, hashFunction.calculateHash(data), data);
    }

    private PatchOperation modify(String path, byte[] oldData, byte[] newData) {
        String hash = hashFunction.calculateHash(newData);

        if (isTextFile(path)) {
            try {
                List<String> originalLines = toLines(oldData);
                List<String> newLines = toLines(newData);

                com.github.difflib.patch.Patch<String> patch = DiffUtils.diff(originalLines, newLines);
                if (patch.getDeltas().isEmpty()) return null; // Should not happen if shouldModify is true

                List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(path, path, originalLines, patch, 3);
                
                // Verification
                List<String> patchedLines = DiffUtils.patch(originalLines, patch);
                if (compareLines(patchedLines, newLines)) {
                    StringBuilder sb = new StringBuilder();
                    for (String line : unifiedDiff) {
                        sb.append(line).append("\n");
                    }
                    return new ModifyOperation(path, hash, sb.toString().getBytes(StandardCharsets.UTF_8), PatchOperation.Method.TEXT_DIFF);
                }
            } catch (Exception ignored) {
                // Fallback
            }
        }

        return new ModifyOperation(path, hash, newData, PatchOperation.Method.REPLACEMENT);
    }

    private boolean compareLines(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    private PatchOperation delete(String path) {
        return new DeleteOperation(path, null);
    }

    private boolean isTextFile(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".json") || p.endsWith(".yaml") || p.endsWith(".yml") ||
                p.endsWith(".txt") || p.endsWith(".properties") || p.endsWith(".xml") ||
                p.endsWith(".cfg") || p.endsWith(".conf") || p.endsWith(".md");
    }

    private List<String> toLines(byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        return Arrays.asList(content.split("\\r?\\n", -1));
    }
}