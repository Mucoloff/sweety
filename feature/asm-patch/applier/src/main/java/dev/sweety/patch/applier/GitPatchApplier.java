package dev.sweety.patch.applier;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.nio.file.*;
import java.util.List;

public class GitPatchApplier {

    public Path apply(Path originalPath, Path patchPath, Path result) throws Exception {
        final List<String> original = Files.readAllLines(originalPath);
        final List<String> patchLines = Files.readAllLines(patchPath);
        final Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
        final List<String> patched = DiffUtils.patch(original, patch);
        return Files.write(result, patched);
    }
}