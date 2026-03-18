package dev.sweety.patch.generator;

import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchDiffEngine;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.type.PatchType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Generator {

    private final String extension;
    private final PatchGenerator generator;

    public Generator(HashFunction hashFunction, ClassNormalizer normalizer, PatchType patchType) {
        this.extension = patchType.extension();
        PatchDiffEngine diffEngine = new PatchDiffEngine(hashFunction, normalizer);
        generator = new PatchGenerator(diffEngine, patchType.writer());
    }

    public File generate(File input, File output, File patchDir, String patch, String fromVersion, String toVersion, PatchFilter filter) throws IOException {
        final File patchFile = new File(patchDir, patch + this.extension);

        try (FileOutputStream out = new FileOutputStream(patchFile)) {
            generator.generate(input, output, out, fromVersion, toVersion, filter);
        }

        return patchFile;
    }

}
