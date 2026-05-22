package dev.sweety.launcher.adapter.out.patch;

import dev.sweety.launcher.port.out.PatchApplierPort;
import dev.sweety.patch.applier.PatchApplier;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Adapter wrapping {@link PatchApplier} to satisfy the {@link PatchApplierPort} driven port.
 */
public class JarPatchApplier implements PatchApplierPort {

    private final PatchApplier delegate;

    public JarPatchApplier(PatchApplier delegate) {
        this.delegate = delegate;
    }

    @Override
    public String extension() {
        return delegate.extension();
    }

    @Override
    public void patch(Path original, Path output, Path patchDir, String patchName) throws IOException {
        delegate.patch(original, output, patchDir, patchName);
    }
}
