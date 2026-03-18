package dev.sweety.patch.filter;

import dev.sweety.patch.diff.PatchFilter;

public class DefaultPatchFilter implements PatchFilter {

    public boolean exclude(String path) {
        return !path.endsWith(".class") || path.contains("META-INF");
    }
}