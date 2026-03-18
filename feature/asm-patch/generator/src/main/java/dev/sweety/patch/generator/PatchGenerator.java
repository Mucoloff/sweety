package dev.sweety.patch.generator;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.archive.JarArchive;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchDiffEngine;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.type.PatchType;
import dev.sweety.patch.verify.PatchValidator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

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

    public File generate(File input, File output, File patchDir, String patch, String fromVersion, String toVersion, PatchFilter filter) throws IOException {
        final File patchFile = new File(patchDir, patch + this.extension);
        final Patch generatedPatch;

        try (FileOutputStream out = new FileOutputStream(patchFile)) {
            generatedPatch = generate(input, output, out, fromVersion, toVersion, filter);
        }

        validator.validate(generatedPatch, new JarArchive(output, filter, normalizer));

        return patchFile;
    }

    public Patch generate(File oldJar, File newJar, OutputStream out, String fromVersion, String toVersion, PatchFilter filter) {

        if (oldJar == null || !oldJar.exists()) throw new IllegalArgumentException("Old JAR file not found: " + oldJar);
        if (newJar == null || !newJar.exists()) throw new IllegalArgumentException("New JAR file not found: " + newJar);

        Archive oldArchive = new JarArchive(oldJar, filter, diffEngine.normalizer());
        Archive newArchive = new JarArchive(newJar, filter, diffEngine.normalizer());

        Patch patch = diffEngine.diff(oldArchive, newArchive, fromVersion, toVersion);

        writer.write(patch, out);
        return patch;
    }
}