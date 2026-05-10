package dev.sweety.minecraft.version;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionComparisonTest {

    @Test
    void testPredicates() {
        MinecraftVersion v18 = MinecraftVersion.V_1_8_8;
        MinecraftVersion v116 = MinecraftVersion.V_1_16_5;

        assertTrue(VersionComparison.NEWER_THAN.test(v116, v18));
        assertTrue(VersionComparison.OLDER_THAN.test(v18, v116));
        assertTrue(VersionComparison.EQUALS.test(v18, v18));
        assertTrue(VersionComparison.NOT_EQUALS.test(v18, v116));

        assertTrue(VersionComparison.NEWER_THAN_OR_EQUALS.test(v116, v18));
        assertTrue(VersionComparison.NEWER_THAN_OR_EQUALS.test(v116, v116));
        
        assertTrue(VersionComparison.OLDER_THAN_OR_EQUALS.test(v18, v116));
        assertTrue(VersionComparison.OLDER_THAN_OR_EQUALS.test(v116, v116));
    }

    @Test
    void testStrategies() {
        MinecraftVersion v18 = MinecraftVersion.V_1_8;
        MinecraftVersion v188 = MinecraftVersion.V_1_8_8;

        // Protocol strategy (both are 47)
        assertTrue(VersionComparison.EQUALS.test(v18, v188, VersionComparison.PROTOCOL));
        assertFalse(VersionComparison.NEWER_THAN.test(v188, v18, VersionComparison.PROTOCOL));

        // Release strategy (v1.8.8 > v1.8)
        assertTrue(VersionComparison.NEWER_THAN.test(v188, v18, VersionComparison.RELEASE));
        
        // Ordinal strategy
        assertTrue(VersionComparison.NEWER_THAN.test(v188, v18, VersionComparison.ORDINAL));
    }

    @Test
    void testSorting() {
        MinecraftVersion v18 = MinecraftVersion.V_1_8;
        MinecraftVersion v116 = MinecraftVersion.V_1_16;

        // NEWER_THAN (Ascending)
        assertTrue(VersionComparison.NEWER_THAN.compare(v116, v18) > 0);
        
        // OLDER_THAN (Descending)
        assertTrue(VersionComparison.OLDER_THAN.compare(v116, v18) < 0);
    }
}
