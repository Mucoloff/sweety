package dev.sweety.extension.manager;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Extension System Integration Tests")
public class IntegrationTest {

    private Path testDir;
    private ExtensionManager<TestIntegrationExtension> manager;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory("luce-integration-test-");
        manager = new ExtensionManager<>(testDir, TestIntegrationExtension.class);
    }

    @Test
    @DisplayName("Should complete full lifecycle: load, toggle, unload")
    void testFullLifecycle() throws Exception {
        Path jarFile = createIntegrationTestJar("LifecycleExt", "1.0.0");

        // Load
        TestIntegrationExtension extension = manager.loadExtension(jarFile);
        assertNotNull(extension);
        assertTrue(extension.enabled());

        // Toggle
        extension.toggle();
        assertFalse(extension.enabled());

        extension.toggle();
        assertTrue(extension.enabled());

        // Unload (via manager save)
        manager.shutdown();
        assertFalse(extension.enabled());
    }

    @Test
    @DisplayName("Should manage multiple extensions independently")
    void testMultipleExtensionsIndependence() throws Exception {
        Path jar1 = createIntegrationTestJar("Ext1", "1.0.0");
        Path jar2 = createIntegrationTestJar("Ext2", "2.0.0");
        Path jar3 = createIntegrationTestJar("Ext3", "3.0.0");

        TestIntegrationExtension ext1 = manager.loadExtension(jar1);
        TestIntegrationExtension ext2 = manager.loadExtension(jar2);
        TestIntegrationExtension ext3 = manager.loadExtension(jar3);

        // Disable only ext2
        ext2.setEnabled(false);

        assertTrue(ext1.enabled());
        assertFalse(ext2.enabled());
        assertTrue(ext3.enabled());

        // Get them back
        assertSame(ext1, manager.get("Ext1"));
        assertSame(ext2, manager.get("Ext2"));
        assertSame(ext3, manager.get("Ext3"));
    }

    @Test
    @DisplayName("Should persist extension info across manager instance")
    void testExtensionInfoPersistence() throws Exception {
        Path jarFile = createIntegrationTestJar("PersistenceExt", "1.5.0");

        TestIntegrationExtension extension = manager.loadExtension(jarFile);
        dev.sweety.extension.ExtensionInfo info1 = manager.get(extension);

        // Get extension again
        TestIntegrationExtension retrieved = manager.get("PersistenceExt");
        dev.sweety.extension.ExtensionInfo info2 = manager.get(retrieved);

        assertNotNull(info1);
        assertNotNull(info2);
        assertEquals(info1.name(), info2.name());
        assertEquals(info1.version(), info2.version());
    }

    @Test
    @DisplayName("Should handle load and save cycle")
    void testLoadSaveCycle() throws Exception {
        Path jar1 = createIntegrationTestJar("CycleExt1", "1.0.0");
        Path jar2 = createIntegrationTestJar("CycleExt2", "1.0.0");

        // Load
        TestIntegrationExtension ext1 = manager.loadExtension(jar1);
        TestIntegrationExtension ext2 = manager.loadExtension(jar2);

        assertTrue(ext1.enabled());
        assertTrue(ext2.enabled());

        // Save (disables all)
        manager.shutdown();

        assertFalse(ext1.enabled());
        assertFalse(ext2.enabled());

        // Create new manager and load from directory
        ExtensionManager<TestIntegrationExtension> manager2 =
            new ExtensionManager<>(testDir, TestIntegrationExtension.class);
        manager2.load();

        TestIntegrationExtension ext1Reloaded = manager2.get("CycleExt1");
        TestIntegrationExtension ext2Reloaded = manager2.get("CycleExt2");

        assertNotNull(ext1Reloaded);
        assertNotNull(ext2Reloaded);
    }

    @Test
    @DisplayName("Should verify extension data folders are isolated")
    void testExtensionDataFolderIsolation() throws Exception {
        Path jar1 = createIntegrationTestJar("FolderExt1", "1.0.0");
        Path jar2 = createIntegrationTestJar("FolderExt2", "1.0.0");

        TestIntegrationExtension ext1 = manager.loadExtension(jar1);
        TestIntegrationExtension ext2 = manager.loadExtension(jar2);

        Path folder1 = ext1.dataFolder();
        Path folder2 = ext2.dataFolder();

        // Folders should be different
        assertNotEquals(folder1.toAbsolutePath().toString(), folder2.toAbsolutePath().toString());

        // Folder names should match extension names
        assertTrue(folder1.toAbsolutePath().toString().contains("FolderExt1"));
        assertTrue(folder2.toAbsolutePath().toString().contains("FolderExt2"));
    }

    @Test
    @DisplayName("Should handle extension state transitions correctly")
    void testExtensionStateTransitions() throws Exception {
        Path jarFile = createIntegrationTestJar("StateExt", "1.0.0");

        TestIntegrationExtension extension = manager.loadExtension(jarFile);

        // Initial: enabled
        assertTrue(extension.enabled());

        // Transition to disabled
        extension.setEnabled(false);
        assertFalse(extension.enabled());

        // Should not call disable again
        int disableCount = extension.getDisableCount();
        extension.setEnabled(false);
        assertEquals(disableCount, extension.getDisableCount());

        // Transition back to enabled
        extension.setEnabled(true);
        assertTrue(extension.enabled());

        // Transition to disabled
        extension.setEnabled(false);
        assertFalse(extension.enabled());
    }

    @Test
    @DisplayName("Should verify extension data folder creation")
    void testDataFolderCreation() throws Exception {
        Path jarFile = createIntegrationTestJar("DataFolderExt", "1.0.0");

        TestIntegrationExtension extension = manager.loadExtension(jarFile);
        Path dataFolder = extension.dataFolder();

        assertTrue(dataFolder.toAbsolutePath().toString().contains("testintegrationextensions"));
        assertTrue(dataFolder.toAbsolutePath().toString().contains("DataFolderExt"));
    }

    /**
     * Test implementation of Extension for integration testing
     */
    static class TestIntegrationExtension extends dev.sweety.extension.Extension {
        private int enableCount = 0;
        private int disableCount = 0;

        protected TestIntegrationExtension(@NotNull String name, @NotNull String version, @Nullable String description, @NotNull Path folder, @NotNull SimpleLogger logger) {
            super(name, version, description, folder, logger);
        }


        @Override
        public void enable() {
            enableCount++;
        }

        @Override
        public void disable() {
            disableCount++;
        }

        public int getEnableCount() {
            return enableCount;
        }

        public int getDisableCount() {
            return disableCount;
        }
    }

    /**
     * Helper method to create integration test JAR
     */
    private Path createIntegrationTestJar(String name, String version) throws IOException {
        Path extensionsDir = testDir.resolve("testintegrationextensions");
        Files.createDirectories(extensionsDir);

        Path jarFile = extensionsDir.resolve(name + "-" + version + ".jar");

        String ymlContent = String.format(
            "{\"name\":\"%s\",\"version\":\"%s\",\"main\":\"%s\"}",
            name, version, TestIntegrationExtension.class.getName()
        );

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            JarEntry entry = new JarEntry("testintegrationextension.yml");
            jos.putNextEntry(entry);
            jos.write(ymlContent.getBytes());
            jos.closeEntry();
        }

        return jarFile;
    }
}
