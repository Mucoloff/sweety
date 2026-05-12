package dev.sweety.extension.versioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class UpdateableExtensionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testFileResolution() throws IOException {
        UpdateableExtensionManager<?> manager = new UpdateableExtensionManager<>(tempDir);

        Path jarPath = tempDir.resolve("test.jar");
        Files.createFile(jarPath);

        Path updatePath = tempDir.resolve("test.jar.update");
        Files.createFile(updatePath);

        Path resolved = manager.resolveFile(jarPath);

        assertEquals(updatePath.toAbsolutePath().normalize(), resolved.toAbsolutePath().normalize());
    }
}
