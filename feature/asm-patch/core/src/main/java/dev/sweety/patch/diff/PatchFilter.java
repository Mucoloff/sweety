package dev.sweety.patch.diff;

public interface PatchFilter {
    boolean exclude(String path);
}