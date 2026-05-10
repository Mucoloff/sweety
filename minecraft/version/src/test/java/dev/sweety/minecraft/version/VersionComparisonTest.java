package dev.sweety.minecraft.version;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionComparisonTest {

    @Test
    void testEquality() {
        MinecraftVersion v1 = MinecraftVersion.V_1_21;
        MinecraftVersion v2 = MinecraftVersion.V_1_21;
        
        assertTrue(VersionComparison.EQUALS.test(v1, v2));
        assertFalse(VersionComparison.NOT_EQUALS.test(v1, v2));
    }

    @Test
    void testComparison() {
        MinecraftVersion v18 = MinecraftVersion.V_1_8;
        MinecraftVersion v116 = MinecraftVersion.V_1_16;

        assertTrue(VersionComparison.NEWER_THAN.test(v116, v18));
        assertTrue(VersionComparison.OLDER_THAN.test(v18, v116));
        
        assertTrue(VersionComparison.NEWER_THAN_OR_EQUALS.test(v116, v116));
        assertTrue(VersionComparison.OLDER_THAN_OR_EQUALS.test(v116, v116));
    }

    @Test
    void testOrdinalStrategy() {
        MinecraftVersion v1 = MinecraftVersion.V_1_16;
        MinecraftVersion v2 = MinecraftVersion.V_1_16_1;
        
        // Logical ordinal should follow the enum definition order
        assertTrue(v2.specific().ordinal() > v1.specific().ordinal());
        assertTrue(VersionComparison.NEWER_THAN.test(v2, v1, VersionComparison.ORDINAL));
    }

    @Test
    void testComparatorBehavior() {
        MinecraftVersion v18 = MinecraftVersion.V_1_8;
        MinecraftVersion v116 = MinecraftVersion.V_1_16;

        // NEWER_THAN (Ascending)
        assertTrue(VersionComparison.NEWER_THAN.compare(v116, v18) > 0);
        
        // OLDER_THAN (Descending)
        assertTrue(VersionComparison.OLDER_THAN.compare(v116, v18) < 0);
    }

    @Test
    void testErrorPaths() {
        assertThrows(NullPointerException.class, () -> VersionComparison.EQUALS.test(null, MinecraftVersion.V_1_21));
        assertThrows(NullPointerException.class, () -> VersionComparison.EQUALS.test(MinecraftVersion.V_1_21, null));
        assertThrows(NullPointerException.class, () -> VersionComparison.EQUALS.test(null));
        assertThrows(NullPointerException.class, () -> VersionComparison.EQUALS.compare(null, MinecraftVersion.V_1_21));
    }
}
