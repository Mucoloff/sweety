package dev.sweety.patch.diff;

import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.DeleteOperation;
import dev.sweety.patch.model.ModifyOperation;
import dev.sweety.patch.model.MoveOperation;
import org.jetbrains.annotations.NotNull;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeSet;

public class PatchDiffEngine {

    private final HashFunction hashFunction;
    private final ClassNormalizer normalizer;

    public PatchDiffEngine(@NotNull HashFunction hashFunction, ClassNormalizer classNormalizer) {
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction cannot be null");
        this.normalizer = classNormalizer;
    }

    public Patch diff(@NotNull Archive oldArchive, @NotNull Archive newArchive, @NotNull String fromVersion, @NotNull String toVersion) {
        Objects.requireNonNull(oldArchive, "oldArchive cannot be null");
        Objects.requireNonNull(newArchive, "newArchive cannot be null");
        Objects.requireNonNull(fromVersion, "fromVersion cannot be null");
        Objects.requireNonNull(toVersion, "toVersion cannot be null");

        List<PatchOperation> ops = new ArrayList<>();
        iterateDiff(oldArchive, newArchive).forEachRemaining(ops::add);
        return new Patch(fromVersion, toVersion, ops);
    }

    /**
     * Lazily emits patch operations without building a full in-memory entry map for both archives.
     */
    public @NotNull Iterator<PatchOperation> iterateDiff(@NotNull Archive oldArchive, @NotNull Archive newArchive) {
        Objects.requireNonNull(oldArchive, "oldArchive cannot be null");
        Objects.requireNonNull(newArchive, "newArchive cannot be null");

        NavigableSet<String> oldNames = oldArchive.entryNames();
        NavigableSet<String> newNames = newArchive.entryNames();
        NavigableSet<String> allPaths = new TreeSet<>(oldNames);
        allPaths.addAll(newNames);
        Iterator<String> pathIter = allPaths.iterator();
        Map<String, PatchOperation> moveOverrides = detectMoves(oldNames, newNames, oldArchive, newArchive);

        return new Iterator<>() {
            PatchOperation nextOp;

            private void ensureNext() {
                if (nextOp != null) {
                    return;
                }
                while (pathIter.hasNext()) {
                    String path = pathIter.next();
                    PatchOperation op;
                    if (moveOverrides.containsKey(path)) {
                        op = moveOverrides.get(path);
                    } else {
                        op = opForPath(path, oldNames, newNames, oldArchive, newArchive);
                    }
                    if (op != null) {
                        nextOp = op;
                        return;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                ensureNext();
                return nextOp != null;
            }

            @Override
            public PatchOperation next() {
                ensureNext();
                if (nextOp == null) throw new NoSuchElementException();
                PatchOperation r = nextOp;
                nextOp = null;
                return r;
            }
        };
    }

    private PatchOperation opForPath(
            String path,
            NavigableSet<String> oldNames,
            NavigableSet<String> newNames,
            Archive oldA,
            Archive newA) {
        boolean inOld = oldNames.contains(path);
        boolean inNew = newNames.contains(path);

        if (!inNew && inOld) return delete(path);

        if (inNew && !inOld)
            return add(path, Objects.requireNonNull(newA.readEntry(path), "missing new entry: " + path));

        if (inOld) {
            long s1 = oldA.uncompressedSize(path);
            long s2 = newA.uncompressedSize(path);
            long c1 = oldA.crc32(path);
            long c2 = newA.crc32(path);
            
            if (s1 == s2 && s1 >= 0 && c1 == c2 && c1 >= 0) {
                return null;
            }
            
            byte[] oldData = oldA.readEntry(path);
            byte[] newData = newA.readEntry(path);
            if (shouldModify(path, oldData, newData)) return modify(path, oldData, newData);
        }
        return null;
    }

    private boolean shouldModify(String path, byte[] oldData, byte[] newData) {
        if (Arrays.equals(oldData, newData)) {
            return false;
        }

        if (path.endsWith(".class") && normalizer != null) {
            byte[] normOld = normalizer.normalize(oldData);
            byte[] normNew = normalizer.normalize(newData);
            return !Arrays.equals(normOld, normNew);
        }

        return true;
    }


    /**
     * Matches paths only in oldArchive against paths only in newArchive by (crc32, size),
     * turning delete+add pairs with identical content into a single MoveOperation.
     */
    private Map<String, PatchOperation> detectMoves(
            NavigableSet<String> oldNames,
            NavigableSet<String> newNames,
            Archive oldArchive,
            Archive newArchive) {
        Map<String, PatchOperation> overrides = new HashMap<>();
        Map<String, Deque<String>> byKey = new HashMap<>();

        for (String path : oldNames) {
            if (newNames.contains(path)) continue;
            long crc = oldArchive.crc32(path);
            long size = oldArchive.uncompressedSize(path);
            if (crc < 0 || size < 0) continue;
            byKey.computeIfAbsent(crc + ":" + size, k -> new ArrayDeque<>()).add(path);
        }

        for (String path : newNames) {
            if (oldNames.contains(path)) continue;
            long crc = newArchive.crc32(path);
            long size = newArchive.uncompressedSize(path);
            if (crc < 0 || size < 0) continue;
            Deque<String> candidates = byKey.get(crc + ":" + size);
            if (candidates == null || candidates.isEmpty()) continue;

            byte[] oldData = oldArchive.readEntry(candidates.peek());
            byte[] newData = newArchive.readEntry(path);
            if (!Arrays.equals(oldData, newData)) continue;

            String oldPath = candidates.poll();
            String hash = hashFunction.calculateHash(newData);
            overrides.put(oldPath, null);
            overrides.put(path, new MoveOperation(oldPath, path, hash));
        }

        return overrides;
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
                if (patch.getDeltas().isEmpty()) {
                    return null;
                }

                List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(path, path, originalLines, patch, 3);

                List<String> patchedLines = DiffUtils.patch(originalLines, patch);
                if (compareLines(patchedLines, newLines)) {
                    StringBuilder sb = new StringBuilder();
                    for (String line : unifiedDiff) {
                        sb.append(line).append("\n");
                    }
                    return new ModifyOperation(path, hash, sb.toString().getBytes(StandardCharsets.UTF_8), PatchOperation.Method.TEXT_DIFF);
                }
            } catch (Exception e) {
                // text-diff failed — fall through to binary REPLACEMENT below
            }
        }

        return new ModifyOperation(path, hash, newData, PatchOperation.Method.REPLACEMENT);
    }

    private boolean compareLines(List<String> a, List<String> b) {
        return a.equals(b);
    }

    private PatchOperation delete(String path) {
        return new DeleteOperation(path, null);
    }

    private boolean isTextFile(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".json") || p.endsWith(".yaml") || p.endsWith(".yml")
                || p.endsWith(".txt") || p.endsWith(".properties") || p.endsWith(".xml")
                || p.endsWith(".cfg") || p.endsWith(".conf") || p.endsWith(".md");
    }

    private List<String> toLines(byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        return Arrays.asList(content.split("\\r?\\n", -1));
    }
}
