package dev.sweety.patch.format.archive;

import java.util.List;

/** Gson DTO for {@link PatchArchiveConstants#INDEX_ENTRY}. */
public final class PatchArchiveIndex {
    public String header;
    public String fromVersion;
    public String toVersion;
    public List<PatchArchiveOpEntry> operations;
}
