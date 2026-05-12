package dev.sweety.patch.format.archive;

import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.exception.PatchFormatException;
import dev.sweety.patch.format.Header;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Merges two patch archives by concatenating operations (segment first, then tail) and renumbering payload entries,
 * without materializing {@link dev.sweety.patch.model.Patch} instances.
 * <p>Order matches {@link dev.sweety.patch.format.PatchEditor#edit(java.nio.file.Path, java.util.function.Consumer)}
 * when the on-disk segment is the first archive and the tail is merged via {@code addAll} of the tail's operations.</p>
 */
public final class PatchArchiveMerger {

    private PatchArchiveMerger() {}

    /**
     * Writes {@code output} as a single patch archive containing all operations from {@code segment} followed by {@code tail}.
     * Metadata {@code fromVersion} / {@code toVersion} are taken from {@code segment}.
     */
    public static void merge(Path segment, Path tail, Path output) throws IOException {
        Path parent = output.getParent();
        if (parent == null) {
            throw new PatchException("Output path has no parent: " + output);
        }
        Path tmp = Files.createTempFile(parent, "patch-merge-", ".tmp");
        boolean moved = false;
        try {
            writeMerged(segment, tail, tmp);
            Files.move(tmp, output, REPLACE_EXISTING, ATOMIC_MOVE);
            moved = true;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void writeMerged(Path segment, Path tail, Path tmpOut) throws IOException {
        try (ZipFile zSeg = new ZipFile(segment.toFile());
             ZipFile zTail = new ZipFile(tail.toFile());
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpOut)))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            PatchArchiveIndex idxSeg = PatchArchiveReader.readIndex(zSeg);
            PatchArchiveIndex idxTail = PatchArchiveReader.readIndex(zTail);
            if (!PatchArchiveConstants.HEADER.equals(idxSeg.header) || !PatchArchiveConstants.HEADER.equals(idxTail.header)) {
                throw new PatchFormatException("Invalid patch archive header");
            }

            List<PatchArchiveOpEntry> outOps = new ArrayList<>();
            int seq = appendOperations(zos, zSeg, idxSeg.operations, 0, outOps);
            appendOperations(zos, zTail, idxTail.operations, seq, outOps);

            PatchArchiveIndex merged = new PatchArchiveIndex();
            merged.header = PatchArchiveConstants.HEADER;
            merged.fromVersion = idxSeg.fromVersion;
            merged.toVersion = idxSeg.toVersion;
            merged.operations = outOps;

            byte[] indexJson = Header.GSON.toJson(merged).getBytes(StandardCharsets.UTF_8);
            ZipEntry indexEntry = new ZipEntry(PatchArchiveConstants.INDEX_ENTRY);
            indexEntry.setTime(0);
            zos.putNextEntry(indexEntry);
            zos.write(indexJson);
            zos.closeEntry();
        }
    }

    private static int appendOperations(
            ZipOutputStream zos,
            ZipFile source,
            List<PatchArchiveOpEntry> ops,
            int payloadSeq,
            List<PatchArchiveOpEntry> outOps) throws IOException {
        for (PatchArchiveOpEntry e : ops) {
            PatchArchiveOpEntry c = copyEntry(e);
            if (!"delete".equalsIgnoreCase(e.type)) {
                if (e.payloadEntry == null || e.payloadEntry.isBlank()) {
                    throw new PatchFormatException("Missing payload for " + e.type + ": " + e.path);
                }
                byte[] bytes = readPayload(source, e.payloadEntry);
                c.payloadEntry = PatchArchiveConstants.PAYLOAD_PREFIX + payloadSeq;
                PatchArchiveEntryNames.requireValidPayloadRef(c.payloadEntry);
                putPayload(zos, c.payloadEntry, bytes);
                payloadSeq++;
            }
            outOps.add(c);
        }
        return payloadSeq;
    }

    private static PatchArchiveOpEntry copyEntry(PatchArchiveOpEntry e) {
        PatchArchiveOpEntry c = new PatchArchiveOpEntry();
        c.type = e.type != null ? e.type.toLowerCase(Locale.ROOT) : null;
        c.path = e.path;
        c.hash = e.hash;
        c.method = e.method;
        c.payloadEntry = "delete".equalsIgnoreCase(e.type) ? null : e.payloadEntry;
        return c;
    }

    private static byte[] readPayload(ZipFile zf, String payloadEntry) throws IOException {
        ZipEntry ze = PatchArchiveEntryNames.requirePayloadZipEntry(zf, payloadEntry);
        try (InputStream in = zf.getInputStream(ze)) {
            return in.readAllBytes();
        }
    }

    private static void putPayload(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        ZipEntry ze = new ZipEntry(entryName);
        ze.setTime(0);
        ze.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(ze);
        zos.write(data);
        zos.closeEntry();
    }
}
