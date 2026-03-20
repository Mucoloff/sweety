package dev.sweety.patch.model.type;

import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.format.PatchWriter;

public interface PatchType {
    String extension();

    PatchReader reader();

    PatchWriter writer();
}
