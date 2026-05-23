package dev.sweety.extension.manager;

import dev.sweety.extension.Extension;
import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.exception.InvalidExtensionException;
import dev.sweety.extension.manager.loader.ExtensionClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionClassLoaderTest {

    /** The descriptor entry name matches the extensionName expected by ExtensionInfo.of(). */
    private static final String EXTENSION_JSON = "extension.json";

    // ------------------------------------------------------------------
    // loadsExtensionFromJar
    // ------------------------------------------------------------------

    @Test
    void loadsExtensionFromJar(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("test.jar");
        TestJarBuilder.buildJar(jar, "test", "1.0.0", TestExtension.class, EXTENSION_JSON);

        ExtensionInfo info = ExtensionInfo.of(jar, "extension");

        try (ExtensionClassLoader<Extension> loader =
                     new ExtensionClassLoader<>(jar, info, Extension.class, tmp)) {

            Extension ext = loader.extension();

            assertNotNull(ext, "extension() must not be null after successful load");
            assertEquals("test", ext.name(), "Extension name should match the descriptor");
            assertEquals("1.0.0", ext.version(), "Extension version should match the descriptor");
        }
    }

    // ------------------------------------------------------------------
    // throwsOnMissingMainClass
    // ------------------------------------------------------------------

    @Test
    void throwsOnMissingMainClass(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("bad.jar");
        // Jar contains a JSON that references a class that does not exist inside the jar
        TestJarBuilder.buildJarWithFakeMain(
                jar, "bad", "0.0.1", "dev.sweety.extension.manager.NonExistentClass", EXTENSION_JSON);

        ExtensionInfo info = ExtensionInfo.of(jar, "extension");

        assertThrows(InvalidExtensionException.class,
                () -> new ExtensionClassLoader<>(jar, info, Extension.class, tmp),
                "ExtensionClassLoader should throw InvalidExtensionException for a missing main class");
    }

    // ------------------------------------------------------------------
    // closeReleasesHandle
    // ------------------------------------------------------------------

    @Test
    void closeReleasesHandle(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("closable.jar");
        TestJarBuilder.buildJar(jar, "closable", "2.0.0", TestExtension.class, EXTENSION_JSON);

        ExtensionInfo info = ExtensionInfo.of(jar, "extension");

        ExtensionClassLoader<Extension> loader =
                new ExtensionClassLoader<>(jar, info, Extension.class, tmp);
        loader.close();

        // On all platforms (including macOS and Windows) if the JarFile handle is still open
        // Files.delete() may fail or the deletion may leave the file locked.
        // After close() the jar file must be deletable.
        assertDoesNotThrow(() -> Files.delete(jar),
                "Jar file should be deletable after ExtensionClassLoader.close() — handle was not released");
    }
}
