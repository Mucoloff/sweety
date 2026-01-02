package dev.sweety.module.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class RuntimeDependencyLoader {

    private final Path libDir;
    private final List<URL> loadedJars = new ArrayList<>();

    public RuntimeDependencyLoader(Path libDir) throws IOException {
        this.libDir = libDir;
        Files.createDirectories(libDir);
    }

    /**
     * Scarica una dependency da Maven Central e la carica a runtime.
     * @param groupId es. org.projectlombok
     * @param artifactId es. lombok
     * @param version es. 1.18.34
     */
    public void loadFromMaven(String groupId, String artifactId, String version)
            throws IOException, ReflectiveOperationException {

        String jarName = artifactId + "-" + version + ".jar";
        Path jarPath = libDir.resolve(jarName);

        if (Files.notExists(jarPath)) {
            String url = String.format(
                    "https://repo1.maven.org/maven2/%s/%s/%s/%s",
                    groupId.replace('.', '/'),
                    artifactId,
                    version,
                    jarName
            );
            System.out.println("Scarico " + artifactId + "...");
            downloadFile(url, jarPath);
        }

        loadJar(jarPath);
    }

    /**
     * Carica un jar locale a runtime.
     */
    public void loadJar(Path jarPath) throws IOException, ReflectiveOperationException {
        URL jarUrl = jarPath.toUri().toURL();

        // Crea un nuovo classloader che eredita da quello corrente
        URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, getParentClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        loadedJars.add(jarUrl);
    }

    /**
     * Restituisce il ClassLoader principale usato per le iniezioni.
     */
    public ClassLoader getParentClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Scarica un file da URL in un path locale.
     */
    private static void downloadFile(String url, Path output) throws IOException {
        try (InputStream in = URI.create(url).toURL().openStream()) {
            Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Ritorna tutti i jar caricati finora.
     */
    public List<URL> getLoadedJars() {
        return List.copyOf(loadedJars);
    }

    /*
    // Esempio d’uso
    public static void main(String[] args) throws Exception {
        RuntimeDependencyLoader loader = new RuntimeDependencyLoader(Path.of("libs"));

        // Carica Lombok
        loader.loadFromMaven("org.projectlombok", "lombok", "1.18.34");

        // Carica Gson
        loader.loadFromMaven("com.google.code.gson", "gson", "2.11.0");

        // Test: carica una classe da Gson
        Class<?> gsonClass = Class.forName("com.google.gson.Gson", true, Thread.currentThread().getContextClassLoader());
        System.out.println("✅ Caricata: " + gsonClass);


        for (URL loadedJar : loader.getLoadedJars()) {
            System.out.println("Jar caricato: " + loadedJar);
        }
    }
     */
}