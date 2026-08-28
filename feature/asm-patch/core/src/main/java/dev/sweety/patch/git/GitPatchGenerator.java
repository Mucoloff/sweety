package dev.sweety.patch.git;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GitPatchGenerator {
    public Path generate(Path oldPath, Path newPath, Path patchPath, int context) throws Exception {
        List<String> original = Files.readAllLines(oldPath);
        List<String> revised = Files.readAllLines(newPath);

        Patch<String> patch = DiffUtils.diff(original, revised);

        List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                oldPath.toString(),
                newPath.toString(),
                original,
                patch,
                context
        );

        try (BufferedWriter writer = Files.newBufferedWriter(patchPath)) {
            for (String line : unifiedDiff) {
                writer.write(line);
                writer.newLine();
            }
        }
        return patchPath;
    }
}