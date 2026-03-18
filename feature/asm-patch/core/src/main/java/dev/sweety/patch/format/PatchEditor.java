package dev.sweety.patch.format;

import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.type.PatchTypes;
import lombok.AllArgsConstructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public record PatchEditor(PatchReader reader, PatchWriter writer) {

    public PatchEditor(PatchTypes t) {
        this(t.reader(), t.writer());
    }

    public Patch read(File patchFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(patchFile)) {
            return reader.read(fis);
        }
    }

    public void write(Patch patch, Path path) throws IOException {
        final Path tmpFile = path.resolveSibling(path.getFileName() + ".tmp");

        try (FileOutputStream fos = new FileOutputStream(tmpFile.toFile())) {
            this.writer.write(patch, fos);
        }

        Files.move(tmpFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void edit(File patchFile, Consumer<Patch> edit) throws IOException {
        final Patch patch = read(patchFile);

        edit.accept(patch);

        write(patch, patchFile.toPath());
    }

}
