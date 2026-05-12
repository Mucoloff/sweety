package dev.sweety.patch.generator;

import dev.sweety.patch.archive.JavaArchive;
import dev.sweety.patch.exception.PatchException;
import dev.sweety.patch.diff.PatchDiffEngine;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.format.archive.PatchArchiveWriter;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.type.PatchType;
import dev.sweety.patch.verify.PatchValidator;

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
    private final PatchWriter writer;

    public PatchGenerator(HashFunction hashFunction, ClassNormalizer normalizer, PatchType patchType) {
        this.extension = patchType.extension();
        this.writer = patchType.writer();
        this.normalizer = normalizer;
        this.diffEngine = new PatchDiffEngine(hashFunction, normalizer);
        this.validator = new PatchValidator(hashFunction);
    }

    public Path generate(Path input, Path output, Path patchDir, String patch, String fromVersion, String toVersion, PatchFilter filter) throws IOException {
        Path patchFile = patchDir.resolve(patch + this.extension);

        Patch legacyPatch = null;
        try (OutputStream out = Files.newOutputStream(patchFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            try (JavaArchive oldArchive = new JavaArchive(input, filter, normalizer);
                 JavaArchive newArchive = new JavaArchive(output, filter, normalizer)) {
                if (writer instanceof PatchArchiveWriter archiveWriter) {
                    archiveWriter.writeStreaming(fromVersion, toVersion, out,
                            diffEngine.iterateDiff(oldArchive, newArchive));
                } else {
                    legacyPatch = diffEngine.diff(oldArchive, newArchive, fromVersion, toVersion);
                    writer.write(legacyPatch, out);
                }
            }
        }

        if (writer instanceof PatchArchiveWriter) {
            try (ZipFile zf = new ZipFile(patchFile.toFile())) {
                validator.validatePatchArchive(zf, output);
            }
        } else {
            try (JavaArchive result = new JavaArchive(output, filter, normalizer)) {
                validator.validate(legacyPatch, result);
            }
        }

        return patchFile;
    }

    /**
     * Generates and writes a patch to {@code out}. When using {@link PatchArchiveWriter} ({@code PATCH_JAR}),
     * this method returns {@code null} because the patch is not materialized as a {@link Patch} instance;
     * use the file-based overload with {@link PatchValidator#validatePatchArchive} when validation is required.
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
            if (writer instanceof PatchArchiveWriter archiveWriter) {
                archiveWriter.writeStreaming(fromVersion, toVersion, out,
                        diffEngine.iterateDiff(oldArchive, newArchive));
                return null;
            }
            Patch patch = diffEngine.diff(oldArchive, newArchive, fromVersion, toVersion);
            writer.write(patch, out);
            return patch;
        } catch (IOException e) {
            throw new PatchException("Failed to close JAR archive", e);
        }
    }
}
