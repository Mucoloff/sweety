package dev.sweety.patch.git;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GitPatchApplier {

    public Path apply(Path originalPath, Path patchPath, Path result) throws Exception {
        final List<String> original = Files.readAllLines(originalPath);
        final List<String> patchLines = Files.readAllLines(patchPath);
        final Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
        final List<String> patched = DiffUtils.patch(original, patch);
        try (BufferedWriter writer = Files.newBufferedWriter(result)) {
            for (String line : patched) {
                writer.write(line);
                writer.newLine();
            }
        }
        return result;
    }
}