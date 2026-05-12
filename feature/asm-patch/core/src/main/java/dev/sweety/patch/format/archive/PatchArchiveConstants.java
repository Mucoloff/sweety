package dev.sweety.patch.format.archive;

/**
 * Layout of a {@link dev.sweety.patch.model.type.PatchTypes#PATCH_JAR PATCH_JAR} artifact:
 * <ul>
 *   <li>{@value #INDEX_ENTRY} — UTF-8 JSON ({@link dev.sweety.patch.format.archive.PatchArchiveIndex}) with header {@value #HEADER}</li>
 *   <li>{@value #PAYLOAD_PREFIX}0 … — raw payload bytes per ADD/MODIFY (names validated by {@link PatchArchiveEntryNames})</li>
 * </ul>
 */
public final class PatchArchiveConstants {

    /** Magic string stored in the JSON index {@code header} field. */
    public static final String HEADER = "ASM-PATCH-ARCHIVE-1";
    /** Index entry inside the patch ZIP; must be readable before payloads. */
    public static final String INDEX_ENTRY = "META-INF/asm-patch-index.json";
    /** Prefix for payload zip entries (see {@link PatchArchiveEntryNames#requireValidPayloadRef}). */
    public static final String PAYLOAD_PREFIX = "p/";

    private PatchArchiveConstants() {}
}
