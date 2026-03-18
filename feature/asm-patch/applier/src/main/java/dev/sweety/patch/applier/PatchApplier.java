package dev.sweety.patch.applier;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.archive.JarArchive;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class PatchApplier {

    private final PatchReader reader;
    private final HashFunction hashFunction;

    public PatchApplier(PatchReader reader, HashFunction hashFunction) {
        this.reader = reader;
        this.hashFunction = hashFunction;
    }

    public void apply(File original, InputStream patchStream, File output) {

        Patch patch = reader.read(patchStream);

        Archive archive = new JarArchive(original);
        Map<String, byte[]> entries = new HashMap<>(archive.entries());

        for (PatchOperation op : patch.getOperations()) {
            switch (op.getType()) {
                case ADD, MODIFY -> {
                    if (op.getData() == null)
                        throw new RuntimeException("Invalid patch: Data is missing for " + op.getType() + " operation on " + op.getPath());

                    // Retrieve original data for patching or replacement verification
                    byte[] originalData = entries.get(op.getPath());
                    if (originalData == null && op.getType().equals(PatchOperation.Type.MODIFY))
                        throw new RuntimeException("Original file not found for patch: " + op.getPath());

                    // Delegate application logic to the Reader (handles binary replacement or text diffs)
                    byte[] data = op.getData();

                    // Verify hash if available
                    if (op.getHash() != null) {
                        String calculatedHash = hashFunction.calculateHash(data);
                        if (!calculatedHash.equalsIgnoreCase(op.getHash())) {
                            throw new RuntimeException("Patch integrity check failed for " + op.getPath()
                                    + ". Expected hash: " + op.getHash() + ", Actual: " + calculatedHash);
                        }
                    }

                    entries.put(op.getPath(), data);
                }
                case DELETE -> entries.remove(op.getPath());
            }
        }

        writeJar(entries, output);
    }

    private void writeJar(Map<String, byte[]> entries, File file) {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jos.putNextEntry(jarEntry);
                jos.write(entry.getValue());
                jos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write output JAR: " + file.getAbsolutePath(), e);
        }
    }
}