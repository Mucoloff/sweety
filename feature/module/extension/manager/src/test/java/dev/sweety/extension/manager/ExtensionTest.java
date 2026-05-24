package dev.sweety.extension.manager;

import dev.sweety.extension.Extension;
import dev.sweety.util.logger.SimpleLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Extension Lifecycle Tests")
public class ExtensionTest {

    private TestExtension extension;
    private Path testFolder;
    private SimpleLogger logger;

    @BeforeEach
    void setUp() {
        testFolder = Paths.get(System.getProperty("java.io.tmpdir"), "test-extension-" + System.nanoTime());
        logger = new SimpleLogger(ExtensionTest.class);
        extension = new TestExtension("TestExtension", "1.0", "desc", testFolder, logger);
    }

    @Test
    @DisplayName("Should create extension with correct name")
    void testExtensionName() {
        assertEquals("TestExtension", extension.name());
    }

    @Test
    @DisplayName("Should create data folder with extension name")
    void testDataFolder() {
        Path expected = testFolder.resolve("TestExtension");
        assertEquals(expected.toAbsolutePath().toString(), extension.dataFolder().toAbsolutePath().toString());
    }

    @Test
    @DisplayName("Should have logger instance")
    void testLogger() {
        assertNotNull(extension.logger());
    }

    @Test
    @DisplayName("Should start disabled")
    void testInitiallyDisabled() {
        assertFalse(extension.enabled());
    }

    @Test
    @DisplayName("Should enable when setEnabled(true)")
    void testEnable() {
        extension.setEnabled(true);
        assertTrue(extension.enabled());
    }

    @Test
    @DisplayName("Should disable when setEnabled(false)")
    void testDisable() {
        extension.setEnabled(true);
        extension.setEnabled(false);
        assertFalse(extension.enabled());
    }

    @Test
    @DisplayName("Should call enable() when enabling")
    void testEnableCallsEnableMethod() {
        TestExtension spy = new TestExtension("TestExtension", "1.0", "desc", testFolder, logger);
        spy.setEnabled(true);
        assertTrue(spy.enableCalled());
    }

    @Test
    @DisplayName("Should call disable() when disabling")
    void testDisableCallsDisableMethod() {
        TestExtension spy = new TestExtension("TestExtension", "1.0", "desc", testFolder, logger);
        spy.setEnabled(true);
        spy.setEnabled(false);
        assertTrue(spy.disableCalled());
    }

    @Test
    @DisplayName("Should toggle extension state")
    void testToggle() {
        assertFalse(extension.enabled());
        extension.toggle();
        assertTrue(extension.enabled());
        extension.toggle();
        assertFalse(extension.enabled());
    }

    @Test
    @DisplayName("Should not call enable if already enabled")
    void testNoDoubleEnable() {
        TestExtension spy = new TestExtension("TestExtension", "1.0", "desc", testFolder, logger);
        spy.setEnabled(true);
        int enableCount = spy.getEnableCount();
        spy.setEnabled(true);
        assertEquals(enableCount, spy.getEnableCount(), "Enable should not be called again");
    }

    @Test
    @DisplayName("Should not call disable if already disabled")
    void testNoDoubleDisable() {
        TestExtension spy = new TestExtension("TestExtension", "1.0", null, testFolder, logger);
        spy.setEnabled(false);
        int disableCount = spy.getDisableCount();
        spy.setEnabled(false);
        assertEquals(disableCount, spy.getDisableCount(), "Disable should not be called again");
    }

    /**
     * Test implementation of Extension for testing purposes
     */
    static class TestExtension extends Extension {
        private int enableCount = 0;
        private int disableCount = 0;

        protected TestExtension(@NotNull String name, @NotNull String version, @Nullable String description, @NotNull Path folder, @NotNull SimpleLogger logger) {
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

        public boolean enableCalled() {
            return enableCount > 0;
        }

        public boolean disableCalled() {
            return disableCount > 0;
        }

        public int getEnableCount() {
            return enableCount;
        }

        public int getDisableCount() {
            return disableCount;
        }
    }
}
