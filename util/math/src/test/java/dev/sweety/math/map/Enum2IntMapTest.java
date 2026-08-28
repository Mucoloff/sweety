package dev.sweety.math.map;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Enum2IntMapTest {

    private enum Key { A, B, C }

    @Test
    void putGetRemove() {
        Enum2IntMap<Key> map = Enum2IntMap.of(Key.class);
        assertEquals(0, map.put(Key.A, 5));
        assertEquals(5, map.get(Key.A));
        assertEquals(5, map.put(Key.A, 9));
        assertEquals(9, map.get(Key.A));
        assertEquals(9, map.remove(Key.A));
        assertEquals(0, map.get(Key.A));
    }

    @Test
    void presenceDistinctFromZero() {
        Enum2IntMap<Key> map = Enum2IntMap.of(Key.class);
        map.put(Key.B, 0);
        assertTrue(map.containsKey(Key.B));
        assertFalse(map.containsKey(Key.C));
        assertEquals(0, map.getOrDefault(Key.B, -1));
        assertEquals(-1, map.getOrDefault(Key.C, -1));
    }

    @Test
    void sizeAccounting() {
        Enum2IntMap<Key> map = Enum2IntMap.of(Key.class);
        assertTrue(map.isEmpty());
        map.put(Key.A, 1);
        map.put(Key.B, 2);
        assertEquals(2, map.size());
        map.put(Key.A, 100); // overwrite, size unchanged
        assertEquals(2, map.size());
        map.remove(Key.A);
        assertEquals(1, map.size());
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void forEachEnumOrder() {
        Enum2IntMap<Key> map = Enum2IntMap.of(Key.class);
        map.put(Key.C, 3);
        map.put(Key.A, 1);
        EnumMap<Key, Integer> seen = new EnumMap<>(Key.class);
        map.forEachInt(seen::put);
        assertEquals(new EnumMap<>(Key.class) {{
            put(Key.A, 1);
            put(Key.C, 3);
        }}, seen);
    }

    @Test
    void javaMapCompat() {
        // Slots into any API expecting a plain java.util.Map<Key, Integer>.
        Map<Key, Integer> map = Enum2IntMap.of(Key.class);
        map.put(Key.A, 1);
        map.put(Key.B, 2);
        assertEquals(1, map.get(Key.A));
        assertNull(map.get(Key.C));
        assertTrue(map.containsKey(Key.A));
        assertTrue(map.containsValue(2));
        assertEquals(2, map.size());
        assertEquals(Map.of(Key.A, 1, Key.B, 2), map);
        Map<Key, Integer> merged = new EnumMap<>(Key.class);
        merged.putAll(map);
        assertEquals(map, merged);
    }
}
