package dev.sweety.patch.format;

import dev.sweety.patch.model.Patch;

import java.io.OutputStream;

public interface PatchWriter {
    void write(Patch patch, OutputStream out);
}