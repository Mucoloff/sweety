package dev.sweety.patch.model.type;

import dev.sweety.patch.format.*;
import dev.sweety.patch.format.bin.*;
import dev.sweety.patch.format.json.*;

import dev.sweety.patch.format.archive.PatchArchiveReader;
import dev.sweety.patch.format.archive.PatchArchiveWriter;

public enum PatchTypes implements PatchType {
    BIN(".patch.bin", new BinaryPatchReader(), new BinaryPatchWriter()),
    JSON(".patch.json", new JsonPatchReader(), new JsonPatchWriter()),
    /**
     * ZIP-based patch: JSON index at {@link PatchArchiveConstants#INDEX_ENTRY} and payload entries under
     * {@link PatchArchiveConstants#PAYLOAD_PREFIX}; suitable for streaming apply via {@code PatchApplier#applyPatchArchive}.
     */
    PATCH_JAR(".patch.jar", new PatchArchiveReader(), new PatchArchiveWriter());

    private final String extension;
    private final PatchReader reader;
    private final PatchWriter writer;

    PatchTypes(String extension, PatchReader reader, PatchWriter writer) {
        this.extension = extension;
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public String extension() {
        return extension;
    }

    @Override
    public PatchReader reader() {
        return reader;
    }

    @Override
    public PatchWriter writer() {
        return writer;
    }
}
