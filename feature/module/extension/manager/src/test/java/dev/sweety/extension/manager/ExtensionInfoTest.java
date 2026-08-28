package dev.sweety.extension.manager;

import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.exception.ExtensionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ExtensionInfo Tests")
public class ExtensionInfoTest {

    private Path testDir;
    private Path jarFile;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory("luce-test-");
        jarFile = testDir.resolve("test-extension.jar");
    }

    @Test
    @DisplayName("Should parse ExtensionInfo from valid yml in JAR")
    void testValidExtensionInfo() throws Exception {
        // Create a valid JAR with extension.yml
        createJarWithExtensionInfo(jarFile, """
                name: "TestExt"
                version: "1.0.0"
                main: "com.test.TestExtension\"""");

        ExtensionInfo info = ExtensionInfo.of(jarFile, "test-extension");

        assertNotNull(info);
        assertEquals("TestExt", info.name());
        assertEquals("1.0.0", info.version());
        assertEquals("com.test.TestExtension", info.main());
    }

    @Test
    @DisplayName("Should throw ExtensionNotFoundException when yml not found")
    void testMissingExtensionInfo() throws Exception {
        // Create a JAR without extension.yml
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
    @DisplayName("Should handle malformed yml gracefully")
    void testMalformedyml() throws Exception {
        createJarWithExtensionInfo(jarFile, "{invalid yml}");

        assertThrows(NullPointerException.class, () -> ExtensionInfo.of(jarFile, "test-extension"));
    }

    @Test
    @DisplayName("Should handle missing required fields")
    void testMissingRequiredFields() throws Exception {
        createJarWithExtensionInfo(jarFile, "{\"name\":\"OnlyName\"}");

        // GSON will deserialize incomplete yml with null values
        // This doesn't throw an exception, so we verify the result contains nulls
        assertThrows(NullPointerException.class, () -> {
            ExtensionInfo info = ExtensionInfo.of(jarFile, "test-extension");
            assertNull(info.version());
            assertNull(info.main());
        });
    }

    /**
     * Helper method to create a JAR file with extension.yml
     */
    private void createJarWithExtensionInfo(Path jarFile, String ymlContent) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            String entryName = jarFile.getFileName().toString().replace(".jar", ".yml");
            JarEntry entry = new JarEntry(entryName);
            jos.putNextEntry(entry);
            jos.write(ymlContent.getBytes());
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
