package dev.sweety.patch.filter;

import dev.sweety.patch.diff.PatchFilter;

public class DefaultPatchFilter implements PatchFilter {

    public boolean exclude(String path) {
        if (!path.endsWith(".class")) return true;

        if (path.contains("BuildInfo")) return true;
        if (path.contains("META-INF")) return true;

        return false;
    }
}