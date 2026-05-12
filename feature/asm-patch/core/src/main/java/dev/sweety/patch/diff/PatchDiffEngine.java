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

        return new Iterator<>() {
            PatchOperation nextOp;

            private void ensureNext() {
                if (nextOp != null) {
                    return;
                }
                while (pathIter.hasNext()) {
                    String path = pathIter.next();
                    PatchOperation op = opForPath(path, oldNames, newNames, oldArchive, newArchive);
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
                if (nextOp == null) {
                    throw new NoSuchElementException();
                }
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
        if (!inNew && inOld) {
            return delete(path);
        }
        if (inNew && !inOld) {
            return add(path, Objects.requireNonNull(newA.readEntry(path), "missing new entry: " + path));
        }
        if (inOld) {
            if (entriesSemanticallyEqual(path, oldA, newA)) {
                return null;
            }
            byte[] oldData = oldA.readEntry(path);
            byte[] newData = newA.readEntry(path);
            if (shouldModify(path, oldData, newData)) {
                return modify(path, oldData, newData);
            }
        }
        return null;
    }

    private boolean entriesSemanticallyEqual(String path, Archive oldA, Archive newA) {
        long s1 = oldA.uncompressedSize(path);
        long s2 = newA.uncompressedSize(path);
        if (s1 >= 0 && s2 >= 0 && s1 != s2) {
            byte[] o = oldA.readEntry(path);
            byte[] n = newA.readEntry(path);
            return !shouldModify(path, o, n);
        }
        long c1 = oldA.crc32(path);
        long c2 = newA.crc32(path);
        if (c1 >= 0 && c2 >= 0 && s1 >= 0 && s2 >= 0 && s1 == s2 && c1 == c2) {
            return true;
        }
        byte[] o = oldA.readEntry(path);
        byte[] n = newA.readEntry(path);
        return !shouldModify(path, o, n);
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
            } catch (Exception ignored) {
                // Fallback
            }
        }

        return new ModifyOperation(path, hash, newData, PatchOperation.Method.REPLACEMENT);
    }

    private boolean compareLines(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
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
