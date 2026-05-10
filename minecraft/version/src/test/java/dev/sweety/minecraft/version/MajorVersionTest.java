package dev.sweety.minecraft.version;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MajorVersionTest {

    @Test
    void testCrossComparison() {
        MinecraftVersion v121 = MinecraftVersion.V_1_21;
        MinecraftVersion v1165 = MinecraftVersion.V_1_16_5;

        assertTrue(v121.isAtLeast(MajorVersion.V_1_16));
        assertTrue(v1165.isAtLeast(MajorVersion.V_1_16));
        assertFalse(MinecraftVersion.V_1_8_8.isAtLeast(MajorVersion.V_1_12));
    }

    @Test
    void testMajorVersionSpecific() {
        assertEquals(MinecraftVersion.V_1_16, MajorVersion.V_1_16.specific());
        assertEquals(16, MajorVersion.V_1_16.minor());
        assertEquals(0, MajorVersion.V_1_16.patch());
    }
}
