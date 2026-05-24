package dev.sweety.extension.manager;

import dev.sweety.extension.Extension;
import dev.sweety.extension.ExtensionInfo;
import dev.sweety.util.logger.SimpleLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

@DisplayName("ExtensionManager Tests")
public class ExtensionManagerTest {

    private ExtensionManager<TestModuleExtension> manager;
    private Path testDir;
    private Path extensionsDir;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory("sweety-manager-test-");
        manager = new ExtensionManager<>(testDir, TestModuleExtension.class);
        extensionsDir = testDir.resolve("testmoduleextensions");
    }

    @Test
    @DisplayName("Should create extensions directory")
    void testExtensionDirectoryCreation() {
        assertTrue(Files.isDirectory(extensionsDir), "Extensions directory should be created");
    }

    @Test
    @DisplayName("Should have correct root directory")
    void testRootDirectory() {
        assertEquals(extensionsDir, manager.getRootDir());
    }

    @Test
    @DisplayName("Should load extension from valid JAR")
    void testLoadExtension() throws Exception {
        Path jarFile = createTestExtensionJar("TestExt", "1.0.0", TestModuleExtension.class.getName());

        TestModuleExtension extension = manager.loadExtension(jarFile);

        assertNotNull(extension);
        assertEquals("TestExt", extension.name());
        assertEquals(extension, manager.get("TestExt"));
    }

    @Test
    @DisplayName("Should return null when loading extension with duplicate name")
    void testLoadDuplicateExtension() throws Exception {
        Path jar1 = createTestExtensionJar("DuplicateExt", "1.0.0", TestModuleExtension.class.getName());
        Path jar2 = createTestExtensionJar("DuplicateExt", "2.0.0", TestModuleExtension.class.getName());

        TestModuleExtension ext1 = manager.loadExtension(jar1);
        assertNotNull(ext1);

        TestModuleExtension ext2 = manager.loadExtension(jar2);
        assertNull(ext2, "Should return null for duplicate extension name");
    }

    @Test
    @DisplayName("Should get extension by name")
    void testGetExtensionByName() throws Exception {
        Path jarFile = createTestExtensionJar("GetTestExt", "1.0.0", TestModuleExtension.class.getName());

        manager.loadExtension(jarFile);
        TestModuleExtension retrieved = manager.get("GetTestExt");

        assertNotNull(retrieved);
        assertEquals("GetTestExt", retrieved.name());
    }

    @Test
    @DisplayName("Should return null when getting non-existent extension")
    void testGetNonExistentExtension() {
        TestModuleExtension ext = manager.get("NonExistent");
        assertNull(ext);
    }

    @Test
    @DisplayName("Should get ExtensionInfo by extension instance")
    void testGetExtensionInfo() throws Exception {
        Path jarFile = createTestExtensionJar("InfoTestExt", "2.5.0", TestModuleExtension.class.getName());

        TestModuleExtension extension = manager.loadExtension(jarFile);
        ExtensionInfo info = manager.get(extension);

        assertNotNull(info);
        assertEquals("InfoTestExt", info.name());
        assertEquals("2.5.0", info.version());
    }

    @Test
    @DisplayName("Should return null when getting info for non-existent extension")
    void testGetInfoNonExistent() {
        TestModuleExtension fakeExt = new TestModuleExtension("Fake", "1.0", null, testDir, new SimpleLogger(ExtensionManagerTest.class));
        ExtensionInfo info = manager.get(fakeExt);
        assertNull(info);
    }

    @Test
    @DisplayName("Should disable all extensions on shutdown")
    void testShutdownDisablesExtensions() throws Exception {
        Path jarFile = createTestExtensionJar("SaveTestExt", "1.0.0", TestModuleExtension.class.getName());

        TestModuleExtension extension = manager.loadExtension(jarFile);
        assertTrue(extension.enabled());

        manager.shutdown();

        assertFalse(extension.enabled());
    }

    @Test
    @DisplayName("Should clear extensions on shutdown")
    void testShutdownClearsExtensions() throws Exception {
        Path jar1 = createTestExtensionJar("ClearExt1", "1.0.0", TestModuleExtension.class.getName());
        Path jar2 = createTestExtensionJar("ClearExt2", "1.0.0", TestModuleExtension.class.getName());

        manager.loadExtension(jar1);
        manager.loadExtension(jar2);

        manager.shutdown();

        assertNull(manager.get("ClearExt1"));
        assertNull(manager.get("ClearExt2"));
    }

    @Test
    @DisplayName("Should load all JAR files in directory")
    void testLoadAllExtensions() throws Exception {
        createTestExtensionJar("MultiExt1", "1.0.0", TestModuleExtension.class.getName());
        createTestExtensionJar("MultiExt2", "1.0.0", TestModuleExtension.class.getName());
        createTestExtensionJar("MultiExt3", "1.0.0", TestModuleExtension.class.getName());

        manager.load();

        assertNotNull(manager.get("MultiExt1"));
        assertNotNull(manager.get("MultiExt2"));
        assertNotNull(manager.get("MultiExt3"));
    }

    @Test
    @DisplayName("Should handle missing JAR files gracefully")
    void testLoadWithoutJars() {
        // Should not throw exception when directory is empty
        assertDoesNotThrow(() -> manager.load());
    }

    @Test
    @DisplayName("Should enable extension after loading")
    void testExtensionEnabledAfterLoad() throws Exception {
        Path jarFile = createTestExtensionJar("EnableTestExt", "1.0.0", TestModuleExtension.class.getName());

        TestModuleExtension extension = manager.loadExtension(jarFile);

        assertTrue(extension.enabled(), "Extension should be enabled after loading");
    }

    /**
     * Test implementation of Extension for manager testing
     */
    static class TestModuleExtension extends Extension {

        protected TestModuleExtension(@NotNull String name, @NotNull String version, @Nullable String description, @NotNull Path folder, @NotNull SimpleLogger logger) {
            super(name, version, description, folder, logger);
        }

        @Override
        public void enable() {
            // Test implementation
        }

        @Override
        public void disable() {
            // Test implementation
        }
    }

    /**
     * Helper method to create a test extension JAR file
     */
    private Path createTestExtensionJar(String name, String version, String mainClass) throws IOException {
        Path jarFile = extensionsDir.resolve(name + "-" + version + ".jar");

        String jsonContent = String.format(
            "{\"name\":\"%s\",\"version\":\"%s\",\"main\":\"%s\"}",
            name, version, mainClass
        );

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            // Add extension.json
            JarEntry entry = new JarEntry("testmoduleextension.json");
            jos.putNextEntry(entry);
            jos.write(jsonContent.getBytes());
            jos.closeEntry();

            // Add a dummy class file
            entry = new JarEntry(mainClass.replace('.', '/') + ".class");
            jos.putNextEntry(entry);
            jos.write(new byte[]{0});
            jos.closeEntry();
        }

        return jarFile;
    }
}
