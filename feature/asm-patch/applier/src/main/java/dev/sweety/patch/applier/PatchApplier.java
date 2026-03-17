package dev.sweety.patch.applier;

import java.io.InputStream;

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
                    entries.put(op.getPath(), op.getData());
                    break;

                case DELETE:
                    entries.remove(op.getPath());
                    break;
            }
        }

        writeJar(entries, outputJar);
    }

    private void writeJar(Map<String, byte[]> entries, File file) {
        // ZipOutputStream
    }
}