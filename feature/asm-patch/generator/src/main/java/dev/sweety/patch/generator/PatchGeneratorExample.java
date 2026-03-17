package dev.sweety.patch.generator;

import dev.sweety.patch.bytecode.AsmClassNormalizer;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.filter.DefaultPatchFilter;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;

import java.io.File;
import java.io.IOException;

public class PatchGeneratorExample {

    public static void main(String[] args) {
        // 1. Setup dependencies
        HashFunction hashFunction = new Sha256Hash();
        ClassNormalizer normalizer = new AsmClassNormalizer();
        PatchFilter filter = new DefaultPatchFilter();

        Generator generator = new Generator(hashFunction, normalizer, filter, PatchTypes.JSON);

        // 5. Define input/output files
        File oldJar = new File("old-app-1.0.0.jar");
        File newJar = new File("new-app-1.0.0.jar");

        try {
            generator.generate(oldJar, newJar, oldJar.getParentFile(), "patch");
        } catch (IOException e) {
            System.err.println("Error generating patch: " + e.getMessage());
            // In production use a logger
        }
    }
}
