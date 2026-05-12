package dev.sweety.patch.format.archive;

import dev.sweety.patch.exception.PatchFormatException;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Constraints for zip entry names inside a patch archive (mitigate malicious index pointing at arbitrary entries).
 */
public final class PatchArchiveEntryNames {

    private PatchArchiveEntryNames() {}

    /**
     * @return the validated name (same as input when valid)
     */
    public static String requireValidPayloadRef(String payloadEntry) {
        if (payloadEntry == null || payloadEntry.isBlank()) {
            throw new PatchFormatException("Invalid payload entry: empty");
        }
        if (payloadEntry.indexOf('\\') >= 0) {
            throw new PatchFormatException("Invalid payload entry: backslashes not allowed");
        }
        if (payloadEntry.startsWith("/")) {
            throw new PatchFormatException("Invalid payload entry: absolute paths not allowed: " + payloadEntry);
        }
        if (payloadEntry.contains("..")) {
            throw new PatchFormatException("Invalid payload entry: must not contain '..': " + payloadEntry);
        }
        if (!payloadEntry.startsWith(PatchArchiveConstants.PAYLOAD_PREFIX)) {
            throw new PatchFormatException("Invalid payload entry: must start with "
                    + PatchArchiveConstants.PAYLOAD_PREFIX + ": " + payloadEntry);
        }
        return payloadEntry;
    }

    public static ZipEntry requirePayloadZipEntry(ZipFile zf, String payloadEntry) throws IOException {
        String safe = requireValidPayloadRef(payloadEntry);
        ZipEntry ze = zf.getEntry(safe);
        if (ze == null) {
            throw new PatchFormatException("Missing payload zip entry: " + safe);
        }
        if (ze.isDirectory()) {
            throw new PatchFormatException("Payload entry must be a file: " + safe);
        }
        if (!safe.equals(ze.getName())) {
            throw new PatchFormatException("Payload entry name mismatch: requested " + safe + " vs " + ze.getName());
        }
        return ze;
    }
}
