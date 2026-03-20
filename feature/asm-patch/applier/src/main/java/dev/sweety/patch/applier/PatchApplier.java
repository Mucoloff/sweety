package dev.sweety.patch.applier;

import dev.sweety.patch.archive.Archive;
import dev.sweety.patch.archive.JarArchive;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.type.PatchType;
import dev.sweety.patch.verify.PatchValidator;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class PatchApplier {

    private final String extension;
    private final PatchReader reader;
    private final PatchValidator validator;
    private final HashFunction hashFunction;

    public PatchApplier(PatchReader reader, String extension, HashFunction hashFunction) {
        this.reader = reader;
        this.extension = extension;
        this.hashFunction = hashFunction;
        this.validator = new PatchValidator(hashFunction);
    }

    public PatchApplier(PatchType patchType, HashFunction hashFunction) {
        this(patchType.reader(), patchType.extension(), hashFunction);
    }

    public String extension() {
        return extension;
    }

    public void patch(File input, File output, File patchDir, String patch) throws IOException {
        final File patchFile = new File(patchDir, patch + this.extension);
        try (FileInputStream patchStream = new FileInputStream(patchFile)) {
            apply(input, patchStream, output);
        }

        try (FileInputStream verifyStream = new FileInputStream(patchFile)) {
            this.validator.validate(this.reader.read(verifyStream), new JarArchive(output));
        }
    }

    public void apply(File original, InputStream patchStream, File output) {
        Patch patch = reader.read(patchStream);

        Archive archive = new JarArchive(original);
        Map<String, byte[]> entries = new TreeMap<>(archive.entries());

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
                case DELETE -> {
                    if (!entries.containsKey(op.getPath()))
                        throw new RuntimeException("Trying to delete non-existing file: " + op.getPath());
                    entries.remove(op.getPath());
                }
            }
        }

        writeJar(entries, output);
    }

    private void writeJar(Map<String, byte[]> entries, File file) {
        File temp = new File(file.getAbsolutePath() + ".tmp");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(temp))) {
            jos.setLevel(9);
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setMethod(ZipEntry.DEFLATED);
                jarEntry.setTime(0);

                jos.putNextEntry(jarEntry);
                jos.write(entry.getValue());
                jos.closeEntry();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to write output JAR: " + file.getAbsolutePath(), e);
        }

        try {
            Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write output JAR: " + file.getAbsolutePath(), e);
        }
    }
}