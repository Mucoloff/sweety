package dev.sweety.patch.generator;

import dev.sweety.patch.bytecode.AsmClassNormalizer;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchDiffEngine;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.filter.DefaultPatchFilter;
import dev.sweety.patch.format.json.JsonPatchWriter;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.hash.Sha256Hash;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PatchGeneratorExample {

    public static void main(String[] args) {
        // 1. Setup dependencies
        HashFunction hashFunction = new Sha256Hash();
        ClassNormalizer normalizer = new AsmClassNormalizer();
        PatchFilter filter = new DefaultPatchFilter();

        // 2. Initialize Diff Engine
        PatchDiffEngine diffEngine = new PatchDiffEngine(hashFunction, normalizer, filter);

        // 3. Initialize Writer (Binary Format)
        PatchWriter writer = new JsonPatchWriter();

        // 4. Create Generator
        PatchGenerator generator = new PatchGenerator(diffEngine, writer);

        // 5. Define input/output files
        File oldJar = new File("old-app-1.0.0.jar");
        File newJar = new File("new-app-1.0.0.jar");
        File patchFile = new File("update.patch.json");

        try (FileOutputStream out = new FileOutputStream(patchFile)) {
            System.out.println("Generating patch...");
            
            // 6. Generate Patch
            generator.generate(oldJar, newJar, out, "1.0", "2.0");
            
            System.out.println("Patch generated successfully: " + patchFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error generating patch: " + e.getMessage());
            // In production use a logger
        }
    }
}
