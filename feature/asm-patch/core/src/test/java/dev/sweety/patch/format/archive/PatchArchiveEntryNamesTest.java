package dev.sweety.patch.format.archive;

import dev.sweety.patch.exception.PatchFormatException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatchArchiveEntryNamesTest {

    @Test
    void validPayloadRef_passes() {
        assertEquals("p/0", PatchArchiveEntryNames.requireValidPayloadRef("p/0"));
        assertEquals("p/12", PatchArchiveEntryNames.requireValidPayloadRef("p/12"));
    }

    @Test
    void invalidPayloadRef_throws() {
        assertThrows(PatchFormatException.class, () -> PatchArchiveEntryNames.requireValidPayloadRef(null));
        assertThrows(PatchFormatException.class, () -> PatchArchiveEntryNames.requireValidPayloadRef(""));
        assertThrows(PatchFormatException.class, () -> PatchArchiveEntryNames.requireValidPayloadRef("../p/0"));
        assertThrows(PatchFormatException.class, () -> PatchArchiveEntryNames.requireValidPayloadRef("p/../0"));
        assertThrows(PatchFormatException.class, () -> PatchArchiveEntryNames.requireValidPayloadRef("/p/0"));
        assertThrows(PatchFormatException.class, () -> PatchArchiveEntryNames.requireValidPayloadRef("META-INF/x"));
        assertThrows(PatchFormatException.class, () -> PatchArchiveEntryNames.requireValidPayloadRef("p/0\\x"));
    }
}
