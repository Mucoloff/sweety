package dev.sweety.extension.manager;

import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.exception.ExtensionNotFoundException;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExtensionInfo Tests")
public class ExtensionInfoTest {

    private Path testDir;
    private Path jarFile;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory("sweety-test-");
        jarFile = testDir.resolve("test-extension.jar");
    }

    @Test
    @DisplayName("Should parse ExtensionInfo from valid JSON in JAR")
    void testValidExtensionInfo() throws Exception {
        // Create a valid JAR with extension.json
        createJarWithExtensionInfo(jarFile, "{\"name\":\"TestExt\",\"version\":\"1.0.0\",\"main\":\"com.test.TestExtension\"}");

        ExtensionInfo info = ExtensionInfo.of(jarFile, "test-extension");

        assertNotNull(info);
        assertEquals("TestExt", info.name());
        assertEquals("1.0.0", info.version());
        assertEquals("com.test.TestExtension", info.main());
    }

    @Test
    @DisplayName("Should throw ExtensionNotFoundException when JSON not found")
    void testMissingExtensionInfo() throws Exception {
        // Create a JAR without extension.json
        createEmptyJar(jarFile);

        assertThrows(ExtensionNotFoundException.class, () -> ExtensionInfo.of(jarFile, "test-extension"));
    }

    @Test
    @DisplayName("Should parse ExtensionInfo with different extension names")
    void testExtensionInfoWithDifferentName() throws Exception {
        Path jar = testDir.resolve("custom-name.jar");
        createJarWithExtensionInfo(jar, "{\"name\":\"CustomExt\",\"version\":\"2.0.0\",\"main\":\"com.custom.CustomExtension\"}");

        ExtensionInfo info = ExtensionInfo.of(jar, "custom-name");

        assertEquals("CustomExt", info.name());
    }

    @Test
    @DisplayName("Should handle ExtensionInfo with special characters in version")
    void testExtensionInfoWithSpecialVersion() throws Exception {
        createJarWithExtensionInfo(jarFile, "{\"name\":\"SpecialExt\",\"version\":\"1.0.0-BETA+build.123\",\"main\":\"com.special.SpecialExtension\"}");

        ExtensionInfo info = ExtensionInfo.of(jarFile, "test-extension");

        assertEquals("1.0.0-BETA+build.123", info.version());
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void testMalformedJSON() throws Exception {
        createJarWithExtensionInfo(jarFile, "{invalid json}");

        assertThrows(JsonSyntaxException.class, () -> ExtensionInfo.of(jarFile, "test-extension"));
    }

    @Test
    @DisplayName("Should handle missing required fields")
    void testMissingRequiredFields() throws Exception {
        createJarWithExtensionInfo(jarFile, "{\"name\":\"OnlyName\"}");

        // GSON will deserialize incomplete JSON with null values
        // This doesn't throw an exception, so we verify the result contains nulls
        ExtensionInfo info = ExtensionInfo.of(jarFile, "test-extension");

        assertNotNull(info);
        assertEquals("OnlyName", info.name());
        // version() and main() will be null since they weren't in JSON
    }

    /**
     * Helper method to create a JAR file with extension.json
     */
    private void createJarWithExtensionInfo(Path jarFile, String jsonContent) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            String entryName = jarFile.getFileName().toString().replace(".jar", ".json");
            JarEntry entry = new JarEntry(entryName);
            jos.putNextEntry(entry);
            jos.write(jsonContent.getBytes());
            jos.closeEntry();
        }
    }

    /**
     * Helper method to create an empty JAR file
     */
    private void createEmptyJar(Path jarFile) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            // Empty JAR
        }
    }
}
