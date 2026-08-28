package dev.sweety.patch.format.archive;

import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.model.MoveOperation;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Patch as a ZIP/JAR: JSON index plus one zip entry per operation payload ({@code p/0}, {@code p/1}, …).
 */
public class PatchArchiveWriter implements PatchWriter {

    /**
     * Writes a patch archive by consuming operations one at a time (for example from a lazy diff iterator),
     * avoiding a full {@link Patch} / operation list in memory.
     */
    public void writeStreaming(String fromVersion, String toVersion, OutputStream out, Iterator<PatchOperation> operations) {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            PatchArchiveIndex index = new PatchArchiveIndex();
            index.header = PatchArchiveConstants.HEADER;
            index.fromVersion = fromVersion;
            index.toVersion = toVersion;

            int payloadSeq = 0;
            List<PatchArchiveOpEntry> entries = new ArrayList<>();
            while (operations.hasNext()) {
                PatchOperation op = operations.next();
                PatchArchiveOpEntry e = op.type() == PatchOperation.Type.MOVE
                        ? new PatchArchiveOpEntry(op.type(), op.path(), op.hash())
                        : new PatchArchiveOpEntry(op.type(), op.path(), op.hash(), op.method());

                if (op.type() == PatchOperation.Type.MOVE) {
                    e.oldPath = ((MoveOperation) op).oldPath();
                } else if (op.type() != PatchOperation.Type.DELETE) {
                    byte[] data = op.data();
                    if (data == null) {
                        throw new PatchException("Patch operation has no data for " + op.type() + ": " + op.path());
                    }
                    String payloadEntry = PatchArchiveConstants.PAYLOAD_PREFIX + payloadSeq++;
                    e.payloadEntry = payloadEntry;
                    PatchArchiveEntryNames.requireValidPayloadRef(payloadEntry);
                    putPayload(zos, payloadEntry, data);
                }
                entries.add(e);
            }
            PatchArchiveMerger.writeIndex(zos, entries, index);
        } catch (IOException e) {
            throw new PatchException("Failed to write patch archive", e);
        }
    }

    @Override
    public void write(Patch patch, OutputStream out) {
        writeStreaming(patch.getFromVersion(), patch.getToVersion(), out, patch.getOperations().iterator());
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
