package dev.sweety.patch.format;

import dev.sweety.patch.model.Patch;

import java.io.InputStream;

public interface PatchReader {
    Patch read(InputStream in);
}