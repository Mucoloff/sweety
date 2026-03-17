package dev.sweety.patch.archive;

import java.util.Map;

public interface Archive {
    Map<String, byte[]> entries();
}