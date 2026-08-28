package dev.sweety.launcher.patch;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Driven port: apply a binary patch to produce an updated artifact.
 */
public interface PatchApplierPort {

    /**
     * Return the file extension used for patch files (e.g. {@code ".patch"}).
     */
    String extension();

    /**
     * Apply the patch file located at {@code patchFile} against the original at {@code original},
     * writing the result into {@code outputDir} under the given {@code outputName}.
     */
    void patch(Path original, Path output, Path patchDir, String patchName) throws IOException;
}
