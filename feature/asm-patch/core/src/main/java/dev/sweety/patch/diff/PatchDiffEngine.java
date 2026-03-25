package dev.sweety.patch.diff;

import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import java.nio.charset.StandardCharsets;
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
                    ops.add(modify(path, oldData, newData));
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

    private PatchOperation modify(String path, byte[] oldData, byte[] newData) {

        PatchOperation.PatchOperationBuilder base = PatchOperation.builder()
                .type(PatchOperation.Type.MODIFY)
                .path(path)
                .hash(hashFunction.calculateHash(newData));

        if (isTextFile(path)) {
            try {
                List<String> originalLines = toLines(oldData);
                List<String> newLines = toLines(newData);

                com.github.difflib.patch.Patch<String> patch = DiffUtils.diff(originalLines, newLines);
                List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(path, path, originalLines, patch, 3);
                
                // --- VERIFICA PREVENTIVA ---
                // Verifica che applicando la patch otteniamo esattamente newData byte-per-byte.
                // Questo protegge da problemi di encoding o line-endings (\r\n vs \n).
                List<String> patchedLines = DiffUtils.patch(originalLines, patch);
                StringBuilder sbCheck = new StringBuilder();
                for (int i = 0; i < patchedLines.size(); i++) {
                    sbCheck.append(patchedLines.get(i));
                    if (i < patchedLines.size() - 1) {
                        sbCheck.append("\n");
                    }
                }
                sbCheck.append("\n"); // PatchApplier aggiunge sempre un newline finale
                
                byte[] reconstructed = sbCheck.toString().getBytes(StandardCharsets.UTF_8);
                
                if (!Arrays.equals(reconstructed, newData)) {
                    // La patch testuale produrrebbe un file diverso (es. line endings). 
                    // Fallback al replacement binario sicuro.
                    // System.out.println("Text diff skipped for " + path + " due to binary mismatch (crlf?)");
                    return base
                            .method(PatchOperation.Method.REPLACEMENT)
                            .data(newData)
                            .build();
                }
                // ---------------------------

                StringBuilder sb = new StringBuilder();
                for (String line : unifiedDiff) {
                    sb.append(line).append("\n");
                }

                return base
                        .method(PatchOperation.Method.TEXT_DIFF)
                        .data(sb.toString().getBytes(StandardCharsets.UTF_8))
                        .build();

            } catch (Exception e) {
                // Fallback a replacement in caso di errore
                e.printStackTrace();
            }
        }

        return base
                .method(PatchOperation.Method.REPLACEMENT)
                .data(newData)
                .build();
    }

    private boolean isTextFile(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".json") ||
                p.endsWith(".yaml") ||
                p.endsWith(".yml") ||
                p.endsWith(".txt") ||
                p.endsWith(".properties") ||
                p.endsWith(".xml") ||
                p.endsWith(".cfg") ||
                p.endsWith(".conf");
    }

    private List<String> toLines(byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8);
        // Gestione fine riga universale per evitare problemi di piattaforma
        return Arrays.asList(content.split("\\r?\\n", -1));
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