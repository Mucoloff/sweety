package dev.sweety.extension.manager;

import dev.sweety.extension.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionManagerTest {

    private static final String EXTENSION_JSON = "extension.json";

    /**
     * Creates an ExtensionManager whose rootDir is {@code tmp/extensions}.
     * The manager infers "extension" as the extensionName from {@code Extension.class.getSimpleName()}.
     */
    private ExtensionManager<Extension> manager(Path tmp) {
        return new ExtensionManager<>(tmp, Extension.class);
    }

    /** Builds a test jar inside the manager's rootDir. */
    private Path jarIn(ExtensionManager<Extension> mgr, String jarName,
                       String extName, String version) throws Exception {
        Path jar = mgr.getRootDir().resolve(jarName);
        TestJarBuilder.buildJar(jar, extName, version, TestExtension.class, EXTENSION_JSON);
        return jar;
    }

    // ------------------------------------------------------------------
    // loadExtension_success
    // ------------------------------------------------------------------

    @Test
    void loadExtension_success(@TempDir Path tmp) throws Exception {
        ExtensionManager<Extension> mgr = manager(tmp);
        Path jar = jarIn(mgr, "alpha.jar", "alpha", "1.0.0");

        Extension result = mgr.loadExtension(jar);

        assertNotNull(result, "loadExtension() should return the loaded extension on success");
        assertTrue(mgr.extensions().containsValue(result),
                "Loaded extension must appear in extensions() registry");
        assertEquals("alpha", result.name());
        assertEquals("1.0.0", result.version());
    }

    // ------------------------------------------------------------------
    // loadExtension_duplicateName_returnsNull
    // ------------------------------------------------------------------

    @Test
    void loadExtension_duplicateName_returnsNull(@TempDir Path tmp) throws Exception {
        ExtensionManager<Extension> mgr = manager(tmp);
        // First load
        Path jar1 = jarIn(mgr, "dup1.jar", "dup", "1.0.0");
        Extension first = mgr.loadExtension(jar1);
        assertNotNull(first, "First load should succeed");

        // Second load with identical name from a different jar file
        Path jar2 = jarIn(mgr, "dup2.jar", "dup", "2.0.0");
        Extension second = mgr.loadExtension(jar2);

        assertNull(second, "loadExtension() must return null when a duplicate extension name is detected");
        assertEquals(1, mgr.extensions().size(),
                "Registry must still contain exactly one entry after duplicate load attempt");
    }

    // ------------------------------------------------------------------
    // loadExtension_missingJar_returnsNull
    // ------------------------------------------------------------------

    @Test
    void loadExtension_missingJar_returnsNull(@TempDir Path tmp) {
        ExtensionManager<Extension> mgr = manager(tmp);
        Path nonExistent = mgr.getRootDir().resolve("ghost.jar");

        Extension result = mgr.loadExtension(nonExistent);

        assertNull(result, "loadExtension() must return null when the jar file does not exist");
        assertTrue(mgr.extensions().isEmpty(), "Registry must remain empty after failed load");
    }

    // ------------------------------------------------------------------
    // unloadExtension_disablesAndRemoves
    // ------------------------------------------------------------------

    @Test
    void unloadExtension_disablesAndRemoves(@TempDir Path tmp) throws Exception {
        ExtensionManager<Extension> mgr = manager(tmp);
        Path jar = jarIn(mgr, "beta.jar", "beta", "3.0.0");

        Extension ext = mgr.loadExtension(jar);
        assertNotNull(ext, "Pre-condition: extension must load successfully");
        assertTrue(ext.enabled(), "Pre-condition: extension must be enabled right after loading");

        Extension removed = mgr.unloadExtension("beta");

        assertSame(ext, removed, "unloadExtension() must return the same object that was loaded");
        assertFalse(ext.enabled(), "Unloaded extension must report enabled=false");
        assertTrue(mgr.extensions().isEmpty(),
                "extensions() registry must be empty after unloading the only extension");
    }

    // ------------------------------------------------------------------
    // shutdown_clearsAll
    // ------------------------------------------------------------------

    @Test
    void shutdown_clearsAll(@TempDir Path tmp) throws Exception {
        ExtensionManager<Extension> mgr = manager(tmp);
        Path jar1 = jarIn(mgr, "x.jar", "x", "1.0.0");
        Path jar2 = jarIn(mgr, "y.jar", "y", "1.0.0");

        Extension e1 = mgr.loadExtension(jar1);
        Extension e2 = mgr.loadExtension(jar2);
        assertNotNull(e1, "Pre-condition: extension x must load");
        assertNotNull(e2, "Pre-condition: extension y must load");
        assertEquals(2, mgr.extensions().size(), "Pre-condition: two extensions in registry");

        mgr.shutdown();

        assertTrue(mgr.extensions().isEmpty(),
                "extensions() must be empty after shutdown()");
        assertTrue(mgr.infos().isEmpty(),
                "infos() must be empty after shutdown()");
        assertFalse(e1.enabled(), "Extension x must be disabled after shutdown()");
        assertFalse(e2.enabled(), "Extension y must be disabled after shutdown()");
    }
}
