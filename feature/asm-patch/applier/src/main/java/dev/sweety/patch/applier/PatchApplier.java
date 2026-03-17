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

    public void apply(File originalJar, InputStream patchStream, File outputJar) {

        Patch patch = reader.read(patchStream);

        Archive archive = new JarArchive(originalJar);
        Map<String, byte[]> entries = new HashMap<>(archive.entries());

        for (PatchOperation op : patch.getOperations()) {
            switch (op.getType()) {
                case ADD:
                case MODIFY:
                    if (op.getData() == null)
                        throw new RuntimeException("Invalid patch: Data is missing for ADD/MODIFY operation on " + op.getPath());
                    
                    // Verify patch integrity
                    String calculatedHash = bytesToHex(hashFunction.hash(op.getData()));
                    if (op.getHash() != null && !calculatedHash.equalsIgnoreCase(op.getHash())) {
                        throw new RuntimeException("Patch corruption detected for " + op.getPath() 
                            + ". Expected hash: " + op.getHash() + ", Actual: " + calculatedHash);
                    }

                    entries.put(op.getPath(), op.getData());
                    break;

                case DELETE:
                    entries.remove(op.getPath());
                    break;
            }
        }

        writeJar(entries, outputJar);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
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