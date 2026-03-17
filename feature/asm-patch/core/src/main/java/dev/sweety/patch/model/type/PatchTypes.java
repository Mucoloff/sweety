package dev.sweety.patch.model.type;

import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.format.bin.BinaryPatchReader;
import dev.sweety.patch.format.bin.BinaryPatchWriter;
import dev.sweety.patch.format.json.JsonPatchReader;
import dev.sweety.patch.format.json.JsonPatchWriter;

public enum PatchTypes implements PatchType {
    BIN(".patch.bin", new BinaryPatchReader(), new BinaryPatchWriter()),
    JSON(".patch.json", new JsonPatchReader(), new JsonPatchWriter());

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
