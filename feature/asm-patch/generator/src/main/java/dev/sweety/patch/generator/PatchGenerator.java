package dev.sweety.patch.generator;

import java.io.OutputStream;

public class PatchGenerator {

    private final PatchDiffEngine diffEngine;
    private final PatchWriter writer;

    public PatchGenerator(PatchDiffEngine diffEngine, PatchWriter writer) {
        this.diffEngine = diffEngine;
        this.writer = writer;
    }

    public void generate(File oldJar, File newJar, OutputStream out,
                         String fromVersion, String toVersion) {

        Archive oldArchive = new JarArchive(oldJar);
        Archive newArchive = new JarArchive(newJar);

        Patch patch = diffEngine.diff(oldArchive, newArchive, fromVersion, toVersion);

        writer.write(patch, out);
    }
}