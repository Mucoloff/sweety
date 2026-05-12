package dev.sweety.patch.archive;

import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;

public interface Archive {

    /**
     * Sorted entry paths (no directories), after any archive filter.
     */
    NavigableSet<String> entryNames();

    /**
     * Payload bytes for the path after filter and optional {@code .class} normalization, or {@code null} if missing or excluded.
     */
    byte[] readEntry(String path);

    /**
     * Uncompressed size if known from the container index without reading the payload, otherwise {@code -1}.
     */
    default long uncompressedSize(String path) {
        return -1;
    }

    /**
     * CRC32 of the uncompressed payload if known from the container, otherwise {@code -1}.
     */
    default long crc32(String path) {
        return -1;
    }

    default Map<String, byte[]> entries() {
        Map<String, byte[]> map = new TreeMap<>();
        for (String name : entryNames()) {
            map.put(name, readEntry(name));
        }
        return map;
    }
}
