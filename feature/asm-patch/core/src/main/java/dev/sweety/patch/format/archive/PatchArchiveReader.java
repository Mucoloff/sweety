package dev.sweety.patch.format.archive;

import dev.sweety.patch.exception.PatchFormatException;
import dev.sweety.patch.format.Header;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.DeleteOperation;
import dev.sweety.patch.model.ModifyOperation;
import dev.sweety.patch.model.MoveOperation;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PatchArchiveReader implements PatchReader {

    private static final Logger LOG = Logger.getLogger(PatchArchiveReader.class.getName());

    public static PatchArchiveIndex readIndex(ZipFile zf) throws IOException {
        ZipEntry ze = zf.getEntry(PatchArchiveConstants.INDEX_ENTRY);
        if (ze == null) throw new PatchFormatException("Patch archive missing " + PatchArchiveConstants.INDEX_ENTRY);
        try (InputStream in = zf.getInputStream(ze)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            PatchArchiveIndex index = Header.GSON.fromJson(json, PatchArchiveIndex.class);
            if (index == null || index.operations == null)
                throw new PatchFormatException("Invalid patch archive index");
            return index;
        }
    }

    @Override
    public Patch read(InputStream in) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("asm-patch-archive", ".zip");
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (ZipFile zf = new ZipFile(tmp.toFile())) {
                PatchArchiveIndex index = readIndex(zf);
                if (!PatchArchiveConstants.HEADER.equals(index.header))
                    throw new PatchFormatException("Invalid patch archive header");
                List<PatchOperation> ops = new ArrayList<>(index.operations.size());
                for (PatchArchiveOpEntry e : index.operations) {
                    ops.add(materializeOperation(zf, e));
                }
                return new Patch(index.fromVersion, index.toVersion, ops);
            }
        } catch (IOException ex) {
            throw new PatchFormatException("Failed to read patch archive", ex);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    LOG.warning("Failed to delete temporary patch archive file '" + tmp + "': " + e.getMessage());
                }
            }
        }
    }

    private static PatchOperation materializeOperation(ZipFile zf, PatchArchiveOpEntry e) throws IOException {
        PatchOperation.Type type = e.type;
        PatchOperation.Method method = e.method;
        return switch (type) {
            case ADD -> {
                byte[] data = readPayload(zf, e);
                yield new AddOperation(e.path, e.hash, data);
            }
            case MODIFY -> {
                byte[] data = readPayload(zf, e);
                yield new ModifyOperation(e.path, e.hash, data, method);
            }
            case DELETE -> new DeleteOperation(e.path, e.hash);
            case MOVE -> {
                if (e.oldPath == null || e.oldPath.isEmpty())
                    throw new PatchFormatException("Missing oldPath for MOVE " + e.path);
                yield new MoveOperation(e.oldPath, e.path, e.hash);
            }
            case null, default -> throw new UnsupportedOperationException("Invalid patch operation type: " + type);
        };
    }

    private static byte[] readPayload(ZipFile zf, PatchArchiveOpEntry e) throws IOException {
        if (e.payloadEntry == null || e.payloadEntry.isEmpty()) {
            throw new PatchFormatException("Missing payloadEntry for " + e.type + " " + e.path);
        }
        ZipEntry pe = PatchArchiveEntryNames.requirePayloadZipEntry(zf, e.payloadEntry);
        try (InputStream pin = zf.getInputStream(pe)) {
            return pin.readAllBytes();
        }
    }
}
