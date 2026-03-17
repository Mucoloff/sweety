package dev.sweety.patch.archive;

import java.util.Map;

public class JarArchive implements Archive {
    // legge jar e ritorna path -> bytes


    @Override
    public Map<String, byte[]> entries() {
        return Map.of();
    }
}