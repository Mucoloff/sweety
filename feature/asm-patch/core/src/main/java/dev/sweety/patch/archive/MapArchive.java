package dev.sweety.patch.archive;

import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.zip.CRC32;

/** In-memory archive for tests and small adapters; exposes size/CRC for fast diff paths. */
public final class MapArchive implements Archive {

    private final Map<String, byte[]> map;
    private final NavigableSet<String> names;

    public MapArchive(Map<String, byte[]> map) {
        this.map = map;
        this.names = new TreeSet<>(map.keySet());
    }

    @Override
    public NavigableSet<String> entryNames() {
        return new TreeSet<>(names);
    }

    @Override
    public byte[] readEntry(String path) {
        return map.get(path);
    }

    @Override
    public long uncompressedSize(String path) {
        byte[] b = map.get(path);
        return b != null ? b.length : -1;
    }

    @Override
    public long crc32(String path) {
        byte[] b = map.get(path);
        if (b == null) {
            return -1;
        }
        CRC32 crc = new CRC32();
        crc.update(b);
        return crc.getValue();
    }
}
