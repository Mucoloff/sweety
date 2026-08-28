package dev.sweety.launcher.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * High-security in-memory ClassLoader.
 *
 * <p>Loads decrypted application JAR byte streams directly into RAM without writing files to disk.
 * Uses {@link #defineClass(String, byte[], int, int)} for direct byte loading and provides a custom
 * {@code memory://} URLStreamHandler for internal resources and manifest reading.
 */
public final class MemoryJarClassLoader extends SecureClassLoader {

    private final Map<String, byte[]> classEntries = new HashMap<>();
    private final Map<String, byte[]> resourceEntries = new HashMap<>();

    public MemoryJarClassLoader(ClassLoader parent) {
        super(parent);
    }

    public static MemoryJarClassLoader fromDecryptedJarBytes(byte[] jarBytes, ClassLoader parent) throws IOException {
        Objects.requireNonNull(jarBytes, "jarBytes must not be null");
        MemoryJarClassLoader loader = new MemoryJarClassLoader(parent);
        loader.loadJarBytes(jarBytes);
        return loader;
    }

    private void loadJarBytes(byte[] jarBytes) throws IOException {
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) continue;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int read;
                while ((read = jis.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                byte[] data = baos.toByteArray();
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    classEntries.put(className, data);
                } else {
                    resourceEntries.put(name, data);
                }
            }
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classEntries.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }
        return super.findClass(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] bytes = resourceEntries.get(name);
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        return super.getResourceAsStream(name);
    }

    @Override
    protected URL findResource(String name) {
        byte[] bytes = resourceEntries.get(name);
        if (bytes != null) {
            try {
                return new URL("memory", "", -1, "/" + name, new MemoryURLStreamHandler(bytes));
            } catch (MalformedURLException e) {
                return null;
            }
        }
        return super.findResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        URL url = findResource(name);
        if (url != null) {
            return Collections.enumeration(Collections.singletonList(url));
        }
        return super.findResources(name);
    }

    private static final class MemoryURLStreamHandler extends URLStreamHandler {
        private final byte[] data;

        MemoryURLStreamHandler(byte[] data) {
            this.data = data;
        }

        @Override
        protected URLConnection openConnection(URL u) {
            return new URLConnection(u) {
                @Override
                public void connect() {}

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(data);
                }
            };
        }
    }
}
