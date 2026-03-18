package dev.sweety.patch.generator;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.archive.JarArchive;
import dev.sweety.patch.diff.PatchDiffEngine;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.model.Patch;

import java.io.File;
import java.io.OutputStream;

public class PatchGenerator {

    private final PatchDiffEngine diffEngine;
    private final PatchWriter writer;

    public PatchGenerator(PatchDiffEngine diffEngine, PatchWriter writer) {
        this.diffEngine = diffEngine;
        this.writer = writer;
    }

    public void generate(File oldJar, File newJar, OutputStream out,
                         String fromVersion, String toVersion, PatchFilter filter) {

        if (oldJar == null || !oldJar.exists()) {
            throw new IllegalArgumentException("Old JAR file not found: " + oldJar);
        }
        if (newJar == null || !newJar.exists()) {
            throw new IllegalArgumentException("New JAR file not found: " + newJar);
        }

        Archive oldArchive = new JarArchive(oldJar);
        Archive newArchive = new JarArchive(newJar);

        Patch patch = diffEngine.diff(oldArchive, newArchive, fromVersion, toVersion, filter);

        writer.write(patch, out);
    }
}