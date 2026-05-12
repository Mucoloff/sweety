package dev.sweety.patch.format.archive;

import dev.sweety.patch.exception.PatchFormatException;
import dev.sweety.patch.format.Header;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PatchArchiveReader implements PatchReader {

    public static PatchArchiveIndex readIndex(ZipFile zf) throws IOException {
        ZipEntry ze = zf.getEntry(PatchArchiveConstants.INDEX_ENTRY);
        if (ze == null) {
            throw new PatchFormatException("Patch archive missing " + PatchArchiveConstants.INDEX_ENTRY);
        }
        try (InputStream in = zf.getInputStream(ze)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            PatchArchiveIndex index = Header.GSON.fromJson(json, PatchArchiveIndex.class);
            if (index == null || index.operations == null) {
                throw new PatchFormatException("Invalid patch archive index");
            }
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
                if (!PatchArchiveConstants.HEADER.equals(index.header)) {
                    throw new PatchFormatException("Invalid patch archive header");
                }
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
                } catch (IOException ignored) {}
            }
        }
    }

    private static PatchOperation materializeOperation(ZipFile zf, PatchArchiveOpEntry e) throws IOException {
        PatchOperation.Type type = PatchOperation.Type.valueOf(e.type.toUpperCase(Locale.ROOT));
        PatchOperation.Method method = "text_diff".equalsIgnoreCase(e.method)
                ? PatchOperation.Method.TEXT_DIFF
                : PatchOperation.Method.REPLACEMENT;
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
