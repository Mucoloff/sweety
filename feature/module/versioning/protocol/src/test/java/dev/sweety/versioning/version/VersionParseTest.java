package dev.sweety.versioning.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VersionParseTest {

    @Test
    void parseValid_triple() {
        Version v = Version.parse("2.4.9");
        assertEquals(2, v.major());
        assertEquals(4, v.minor());
        assertEquals(9, v.patch());
    }

    @Test
    void parseInvalid_returnsZero() {
        assertEquals(Version.ZERO, Version.parse("not-a-version"));
        assertEquals(Version.ZERO, Version.parse(""));
    }

    @Test
    void newerThan() {
        assertTrue(new Version(2, 0, 0).newerThan(new Version(1, 9, 9)));
        assertTrue(new Version(1, 2, 0).newerThan(new Version(1, 1, 0)));
        assertTrue(new Version(1, 0, 3).newerThan(new Version(1, 0, 2)));
        assertFalse(new Version(1, 0, 0).newerThan(new Version(1, 0, 0)));
    }
}
