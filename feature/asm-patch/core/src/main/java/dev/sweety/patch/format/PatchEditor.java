package dev.sweety.patch.format;

import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.type.PatchTypes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public record PatchEditor(PatchReader reader, PatchWriter writer) {

    public PatchEditor(PatchTypes t) {
        this(t.reader(), t.writer());
    }

    public Patch read(Path patchFile) throws IOException {
        try (InputStream in = Files.newInputStream(patchFile)) {
            return reader.read(in);
        }
    }

    public void write(Patch patch, Path path) throws IOException {
        Path tmpFile = path.resolveSibling(path.getFileName() + ".tmp");

        try (OutputStream fos = Files.newOutputStream(tmpFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            this.writer.write(patch, fos);
        }

        Files.move(tmpFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void edit(Path patchFile, Consumer<Patch> edit) throws IOException {
        Patch patch = read(patchFile);

        edit.accept(patch);

        write(patch, patchFile);
    }

}
