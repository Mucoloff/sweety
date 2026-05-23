package dev.sweety.extension.manager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Utility that builds minimal test jars in-memory and writes them to a given path.
 * The jar contains:
 *  - A descriptor JSON file (e.g. {@code extension.json}) consumed by {@code ExtensionInfo.of()}.
 *  - The compiled .class bytes of the supplied {@code mainClass} copied from the test classpath.
 */
public final class TestJarBuilder {

    private TestJarBuilder() {}

    /**
     * Builds a jar at {@code dest} that contains:
     * <ol>
     *   <li>A JSON descriptor entry named {@code extensionJsonName} (e.g. {@code "extension.json"}).</li>
     *   <li>The compiled class bytes for {@code mainClass} and <em>all</em> of its declared inner/nested
     *       classes found on the test classpath (best-effort).</li>
     * </ol>
     *
     * @param dest              target path where the jar file will be written
     * @param name              value of the "name" field in the descriptor JSON
     * @param version           value of the "version" field in the descriptor JSON
     * @param mainClass         class whose binary name is placed in the "main" field and whose .class
     *                          bytes are embedded into the jar
     * @param extensionJsonName name of the JSON entry inside the jar (e.g. {@code "extension.json"})
     */
    public static Path buildJar(Path dest, String name, String version, Class<?> mainClass, String extensionJsonName)
            throws IOException {

        String json = buildJson(name, version, mainClass.getName(), null);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(dest), manifest)) {
            // Write the descriptor JSON
            addEntry(jos, extensionJsonName, json.getBytes(StandardCharsets.UTF_8));

            // Write the main class bytes
            writeClassBytes(jos, mainClass);
        }

        return dest;
    }

    /**
     * Variant that accepts an explicit fully-qualified main class name for the JSON but does NOT
     * embed any class bytes for that name. Useful for testing "bad main class" scenarios.
     */
    public static Path buildJarWithFakeMain(Path dest, String name, String version,
                                            String fakeMainClassName, String extensionJsonName)
            throws IOException {

        String json = buildJson(name, version, fakeMainClassName, null);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(dest), manifest)) {
            addEntry(jos, extensionJsonName, json.getBytes(StandardCharsets.UTF_8));
            // Intentionally no class bytes — Class.forName will throw ClassNotFoundException
        }

        return dest;
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static String buildJson(String name, String version, String main, String description) {
        if (description == null) {
            return String.format(
                    "{\"name\":\"%s\",\"version\":\"%s\",\"main\":\"%s\"}",
                    name, version, main);
        }
        return String.format(
                "{\"name\":\"%s\",\"version\":\"%s\",\"main\":\"%s\",\"description\":\"%s\"}",
                name, version, main, description);
    }

    /**
     * Writes the .class file for {@code cls} (resolved from the test classloader) into the jar.
     * Nested/anonymous/lambda classes are silently skipped if not found — the main class bytes
     * are the only hard requirement.
     */
    private static void writeClassBytes(JarOutputStream jos, Class<?> cls) throws IOException {
        String resourcePath = cls.getName().replace('.', '/') + ".class";
        ClassLoader loader = TestJarBuilder.class.getClassLoader();

        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Cannot locate class bytes for " + cls.getName()
                        + " — is the class compiled to the test classpath?");
            }
            addEntry(jos, resourcePath, in.readAllBytes());
        }
    }

    private static void addEntry(JarOutputStream jos, String entryName, byte[] bytes) throws IOException {
        JarEntry entry = new JarEntry(entryName);
        jos.putNextEntry(entry);
        jos.write(bytes);
        jos.closeEntry();
    }
}
