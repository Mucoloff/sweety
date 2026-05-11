package dev.sweety.minecraft.version;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class MinecraftVersionTest {

    @Test
    void testProtocolLookup() {
        // 1.8.8 -> 47
        MinecraftVersion v188 = MinecraftVersion.get(47);
        assertEquals(MinecraftVersion.V_1_8_9, v188);

        // 1.16.5 -> 754
        MinecraftVersion v1165 = MinecraftVersion.get(754);
        assertEquals(MinecraftVersion.V_1_16_5, v1165);

        // Error for unknown protocol
        assertEquals(MinecraftVersion.ERROR, MinecraftVersion.get(-1));
    }

    @Test
    void testNameLookup() {
        assertEquals(MinecraftVersion.V_1_16_5, MinecraftVersion.get("1.16.5"));
        assertEquals(MinecraftVersion.V_1_8_8, MinecraftVersion.get("V_1_8_8"));
        assertEquals(MinecraftVersion.V_1_21, MinecraftVersion.get("1.21"));
        assertEquals(MinecraftVersion.ERROR, MinecraftVersion.get("unknown"));
    }

    @Test
    void testSemanticVersioning() {
        MinecraftVersion v = MinecraftVersion.V_1_16_5;
        assertEquals(1, v.major());
        assertEquals(16, v.minor());
        assertEquals(5, v.patch());

        MinecraftVersion v121 = MinecraftVersion.V_1_21;
        assertEquals(1, v121.major());
        assertEquals(21, v121.minor());
        assertEquals(0, v121.patch());
    }

    @Test
    void testComparison() {
        assertTrue(MinecraftVersion.V_1_16_5.isNewerThan(MinecraftVersion.V_1_8_8));
        assertTrue(MinecraftVersion.V_1_8_8.isOlderThan(MinecraftVersion.V_1_16_5));
        assertTrue(MinecraftVersion.V_1_16_5.isAtLeast(MinecraftVersion.V_1_16));
        assertFalse(MinecraftVersion.V_1_8_8.isAtLeast(MinecraftVersion.V_1_16));
    }

    @Test
    void testGetAll() {
        List<MinecraftVersion> v18s = MinecraftVersion.getAll(47);
        assertTrue(v18s.contains(MinecraftVersion.V_1_8));
        assertTrue(v18s.contains(MinecraftVersion.V_1_8_3));
        assertTrue(v18s.contains(MinecraftVersion.V_1_8_8));
        assertTrue(v18s.contains(MinecraftVersion.V_1_8_9));
        assertFalse(v18s.contains(MinecraftVersion.V_1_16_5));
    }
}
