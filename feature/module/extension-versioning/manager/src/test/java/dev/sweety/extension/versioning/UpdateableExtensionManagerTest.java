package dev.sweety.extension.versioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class UpdateableExtensionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testFileResolution() throws IOException {
        UpdateableExtensionManager<?> manager = new UpdateableExtensionManager<>(tempDir.toFile());
        
        Path jarPath = tempDir.resolve("test.jar");
        Files.createFile(jarPath);
        
        // Mock an update file
        Path updatePath = tempDir.resolve("test.jar.update");
        Files.createFile(updatePath);
        
        File resolved = manager.resolveFile(jarPath.toFile());
        
        // Should resolve to the .update file if it exists
        assertEquals(updatePath.toFile().getAbsolutePath(), resolved.getAbsolutePath());
    }
}
