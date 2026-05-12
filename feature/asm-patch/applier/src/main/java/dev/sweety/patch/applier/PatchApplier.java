package dev.sweety.patch.applier;

import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.exception.PatchFormatException;
import dev.sweety.patch.exception.PatchValidationException;
import dev.sweety.patch.format.archive.PatchArchiveConstants;
import dev.sweety.patch.format.archive.PatchArchiveEntryNames;
import dev.sweety.patch.format.archive.PatchArchiveIndex;
import dev.sweety.patch.format.archive.PatchArchiveOpEntry;
import dev.sweety.patch.format.archive.PatchArchiveReader;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.type.PatchType;
import dev.sweety.patch.verify.PatchValidator;
import com.github.difflib.UnifiedDiffUtils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PatchApplier {

    private final String extension;
    private final PatchValidator validator;
    private final HashFunction hashFunction;

    public PatchApplier(PatchType patchType, HashFunction hashFunction) {
        this.extension = patchType.extension();
        this.hashFunction = hashFunction;
        this.validator = new PatchValidator(hashFunction);
    }

    public String extension() {
        return extension;
    }

    public void patch(Path input, Path output, Path patchDir, String patch) throws IOException {
        Path patchFile = patchDir.resolve(patch + this.extension);
        applyPatchArchive(input, patchFile, output);
        try (ZipFile zf = new ZipFile(patchFile.toFile())) {
            validator.validatePatchArchive(zf, output);
        }
    }

    public void apply(Path original, InputStream patchStream, Path output) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("asm-patch-archive", ".zip");
            Files.copy(patchStream, tmp, REPLACE_EXISTING);
            applyPatchArchive(original, tmp, output);
        } catch (IOException e) {
            throw new PatchException("Failed to apply patch archive from stream", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Apply a patch archive (JSON index + payload entries) without materializing a full in-memory patch model.
     */
    public void applyPatchArchive(Path original, Path patchArchive, Path output) {
        try (JarFile base = new JarFile(original.toFile());
             ZipFile pz = new ZipFile(patchArchive.toFile())) {
            PatchArchiveIndex idx = PatchArchiveReader.readIndex(pz);
            if (!PatchArchiveConstants.HEADER.equals(idx.header)) {
                throw new PatchFormatException("Invalid patch archive header");
            }
            verifyDeletePreconditionsArchive(base, idx);

            Map<String, PatchArchiveOpEntry> patchByPath = new HashMap<>();
            Set<String> needOriginal = new HashSet<>();
            for (PatchArchiveOpEntry e : idx.operations) {
                if ("delete".equalsIgnoreCase(e.type)) {
                    continue;
                }
                patchByPath.put(e.path, e);
                if ("modify".equalsIgnoreCase(e.type) && "text_diff".equalsIgnoreCase(e.method)) {
                    needOriginal.add(e.path);
                }
            }

            Map<String, byte[]> originals = readOriginalsForPaths(base, needOriginal);
            TreeSet<String> outputPaths = buildOutputPathsArchive(base, idx);

            Path temp = output.resolveSibling(output.getFileName().toString() + ".tmp");
            try {
                try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                    jos.setLevel(9);
                    for (String path : outputPaths) {
                        PatchArchiveOpEntry e = patchByPath.get(path);
                        if (e != null && ("add".equalsIgnoreCase(e.type) || "modify".equalsIgnoreCase(e.type))) {
                            byte[] payload = readZipPayload(pz, e.payloadEntry);
                            byte[] finalData = resolveArchiveEntryBytes(e, payload, originals);
                            assertHash(finalData, e.hash);
                            writeJarEntry(jos, path, finalData);
                        } else {
                            streamCopyEntry(base, path, jos);
                        }
                    }
                }
                Files.move(temp, output, REPLACE_EXISTING, ATOMIC_MOVE);
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException e) {
            throw new PatchException("Failed to apply patch archive", e);
        }
    }

    private void verifyDeletePreconditionsArchive(JarFile base, PatchArchiveIndex idx) throws IOException {
        for (PatchArchiveOpEntry e : idx.operations) {
            if (!"delete".equalsIgnoreCase(e.type)) {
                continue;
            }
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

    private TreeSet<String> buildOutputPathsArchive(JarFile base, PatchArchiveIndex idx) {
        Set<String> deleted = new HashSet<>();
        for (PatchArchiveOpEntry e : idx.operations) {
            if ("delete".equalsIgnoreCase(e.type)) {
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
            if ("add".equalsIgnoreCase(e.type)) {
                out.add(e.path);
            }
        }
        return out;
    }

    private byte[] resolveArchiveEntryBytes(PatchArchiveOpEntry e, byte[] payload, Map<String, byte[]> originals) {
        if ("modify".equalsIgnoreCase(e.type)) {
            if ("text_diff".equalsIgnoreCase(e.method)) {
                byte[] originalData = originals.get(e.path);
                if (originalData == null) {
                    throw new PatchException("Original file not found for modification: " + e.path);
                }
                return applyTextDiff(e.path, originalData, payload);
            }
            return payload;
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
        if (expectedHash != null) {
            String calculatedHash = hashFunction.calculateHash(data);
            if (!calculatedHash.equalsIgnoreCase(expectedHash)) {
                throw new PatchValidationException("Patch integrity check failed"
                        + ". Expected hash: " + expectedHash + ", Actual: " + calculatedHash);
            }
        }
    }

    private void writeJarEntry(JarOutputStream jos, String path, byte[] data) throws IOException {
        JarEntry jarEntry = new JarEntry(path);
        jarEntry.setMethod(ZipEntry.DEFLATED);
        jarEntry.setTime(0);
        jos.putNextEntry(jarEntry);
        jos.write(data);
        jos.closeEntry();
    }

    private void streamCopyEntry(JarFile base, String path, JarOutputStream jos) throws IOException {
        JarEntry src = base.getJarEntry(path);
        if (src == null) {
            throw new PatchException("Missing entry in base JAR: " + path);
        }
        JarEntry dest = new JarEntry(path);
        dest.setMethod(ZipEntry.DEFLATED);
        dest.setTime(0);
        jos.putNextEntry(dest);
        try (InputStream in = base.getInputStream(src)) {
            in.transferTo(jos);
        }
        jos.closeEntry();
    }

    private byte[] applyTextDiff(String path, byte[] originalData, byte[] diffData) {
        try {
            List<String> originalLines = toLines(originalData);
            List<String> diffLines = toLines(diffData);
            com.github.difflib.patch.Patch<String> patchObj = UnifiedDiffUtils.parseUnifiedDiff(diffLines);
            List<String> patchedLines = com.github.difflib.DiffUtils.patch(originalLines, patchObj);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < patchedLines.size(); i++) {
                sb.append(patchedLines.get(i));
                if (i < patchedLines.size() - 1) {
                    sb.append("\n");
                }
            }
            sb.append("\n");
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PatchException("Failed to apply text diff for " + path, e);
        }
    }

    private List<String> toLines(byte[] data) {
         String content = new String(data, StandardCharsets.UTF_8);
         return Arrays.asList(content.split("\\r?\\n", -1));
    }
}
