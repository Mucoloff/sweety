package dev.sweety.patch.generator;

import dev.sweety.patch.archive.JavaArchive;
import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.diff.PatchDiffEngine;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.format.archive.PatchArchiveWriter;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.type.PatchType;
import dev.sweety.patch.verify.PatchValidator;
import dev.sweety.patch.verify.Validators;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipFile;

public class PatchGenerator {

    private final String extension;
    private final PatchValidator validator;
    private final ClassNormalizer normalizer;
    private final PatchDiffEngine diffEngine;
    private final PatchArchiveWriter writer;

    public PatchGenerator(HashFunction hashFunction, ClassNormalizer normalizer, PatchType patchType) {
        this.extension = patchType.extension();
        if (!(patchType.writer() instanceof PatchArchiveWriter archiveWriter)) {
            throw new IllegalArgumentException("Only patch archive format is supported; got: " + patchType);
        }
        this.writer = archiveWriter;
        this.normalizer = normalizer;
        this.diffEngine = new PatchDiffEngine(hashFunction, normalizer);
        this.validator = Validators.forHash(hashFunction);
    }

    public Path generate(Path input, Path output, Path patchDir, String patch, String fromVersion, String toVersion, PatchFilter filter) throws IOException {
        Path patchFile = patchDir.resolve(patch + this.extension);

        try (OutputStream out = Files.newOutputStream(patchFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             JavaArchive oldArchive = new JavaArchive(input, filter, normalizer);
             JavaArchive newArchive = new JavaArchive(output, filter, normalizer)) {
            writer.writeStreaming(fromVersion, toVersion, out, diffEngine.iterateDiff(oldArchive, newArchive));
        }

        try (ZipFile zf = new ZipFile(patchFile.toFile())) {
            validator.validatePatchArchive(zf, output);
        }

        return patchFile;
    }

    /**
     * Writes a patch archive to {@code out}. Does not return a materialized {@link Patch} (streaming diff).
     */
    public Patch generate(Path oldJar, Path newJar, OutputStream out, String fromVersion, String toVersion, PatchFilter filter) {

        if (oldJar == null || !Files.exists(oldJar)) {
            throw new IllegalArgumentException("Old JAR file not found: " + oldJar);
        }
        if (newJar == null || !Files.exists(newJar)) {
            throw new IllegalArgumentException("New JAR file not found: " + newJar);
        }

        try (JavaArchive oldArchive = new JavaArchive(oldJar, filter, normalizer);
             JavaArchive newArchive = new JavaArchive(newJar, filter, normalizer)) {
            writer.writeStreaming(fromVersion, toVersion, out, diffEngine.iterateDiff(oldArchive, newArchive));
            return null;
        } catch (IOException e) {
            throw new PatchException("Failed to close JAR archive", e);
        }
    }
}
