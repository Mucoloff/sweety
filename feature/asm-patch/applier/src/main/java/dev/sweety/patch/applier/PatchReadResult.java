package dev.sweety.patch.applier;

import java.util.Map;
import java.util.TreeSet;

/**
 * Immutable result produced by {@link PatchSourceReader}: the ordered set of paths that must
 * appear in the output JAR, and the pre-resolved bytes for entries that are added or modified
 * by the patch (paths absent from {@code patchedBytes} must be copied verbatim from the base JAR).
 */
record PatchReadResult(TreeSet<String> outputPaths, Map<String, byte[]> patchedBytes) {
}
