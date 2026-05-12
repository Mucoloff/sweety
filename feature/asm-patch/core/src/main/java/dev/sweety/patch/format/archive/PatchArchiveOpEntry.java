package dev.sweety.patch.format.archive;

/** Single operation in a patch archive index. */
@SuppressWarnings("unused")
public final class PatchArchiveOpEntry {
    /** "add" | "modify" | "delete" */
    public String type;
    public String path;
    public String hash;
    /** "replacement" | "text_diff"; omitted defaults to replacement */
    public String method;
    /** Zip entry path under the same archive, e.g. {@code p/0}; absent for delete */
    public String payloadEntry;
}
