package dev.sweety.patch.applier;

import dev.sweety.patch.archive.JarArchive;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.model.type.PatchType;
import dev.sweety.patch.verify.PatchValidator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Applier {

    private final String extension;
    private final PatchApplier applier;
    private final PatchReader reader;
    private final PatchValidator validator;

    public Applier(PatchType patchType, HashFunction hashFunction) {
        this.extension = patchType.extension();
        this.reader = patchType.reader();
        this.validator = new PatchValidator(hashFunction);
        this.applier = new PatchApplier(this.reader, hashFunction);
    }


    public void patch(File input, File output, File patchDir, String patch) throws IOException {
        final File patchFile = new File(patchDir, patch + this.extension);
        try (FileInputStream patchStream = new FileInputStream(patchFile)) {
            applier.apply(input, patchStream, output);
        }

        try (FileInputStream verifyStream = new FileInputStream(patchFile)) {
            validator.validate(reader.read(verifyStream), new JarArchive(output));
        }
    }


}
