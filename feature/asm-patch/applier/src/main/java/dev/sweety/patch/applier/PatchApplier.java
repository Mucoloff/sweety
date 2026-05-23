package dev.sweety.patch.applier;

import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.type.PatchType;
import dev.sweety.patch.verify.PatchValidator;
import dev.sweety.patch.verify.Validators;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PatchApplier {

    private final String extension;
    private final PatchValidator validator;
    private final PatchSourceReader sourceReader;
    private final JarWriter jarWriter;

    public PatchApplier(PatchType patchType, HashFunction hashFunction) {
        this.extension = patchType.extension();
        this.validator = Validators.forHash(hashFunction);
        this.sourceReader = new PatchSourceReader(hashFunction);
        this.jarWriter = new JarWriter();
    }

    public String extension() {
        return extension;
    }

    public void patch(Path input, Path output, Path patchDir, String patch) throws IOException {
        Path patchFile = patchDir.resolve(patch + this.extension);
        applyPatchArchive(input, patchFile, output);
        try (ZipFile zf = new ZipFile(patchFile.toFile())) {
            validator.validatePatchArchive(zf, output);
        }
    }

    public void apply(Path original, InputStream patchStream, Path output) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("asm-patch-archive", ".zip");
            Files.copy(patchStream, tmp, REPLACE_EXISTING);
            applyPatchArchive(original, tmp, output);
        } catch (IOException e) {
            throw new PatchException("Failed to apply patch archive from stream", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Apply a patch archive (JSON index + payload entries) without materializing a full in-memory patch model.
     */
    public void applyPatchArchive(Path original, Path patchArchive, Path output) {
        try (JarFile base = new JarFile(original.toFile());
             ZipFile pz = new ZipFile(patchArchive.toFile())) {
            PatchReadResult result = sourceReader.read(base, pz);
            jarWriter.write(base, result, output);
        } catch (IOException e) {
            throw new PatchException("Failed to apply patch archive", e);
        }
    }
}
