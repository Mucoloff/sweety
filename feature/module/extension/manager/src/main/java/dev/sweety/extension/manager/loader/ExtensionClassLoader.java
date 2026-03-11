package dev.sweety.extension.manager.loader;

import dev.sweety.logger.SimpleLogger;
import dev.sweety.extension.Extension;
import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.exception.InvalidExtensionException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ExtensionClassLoader<T extends Extension> extends URLClassLoader {

    private final JarFile jar;
    private final Manifest manifest;
    private final URL url;
    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();

    private final T extension;

    public ExtensionClassLoader(final File jarFile, final ExtensionInfo info, final Class<T> parent, File rootDir) throws Exception {
        super(new URL[]{jarFile.toURI().toURL()}, parent.getClassLoader());
        this.jar = new JarFile(jarFile);
        this.manifest = this.jar.getManifest();
        this.url = jarFile.toURI().toURL();

        final Class<?> mainClass;
        try {
            mainClass = Class.forName(info.main(), true, this);
        } catch (ClassNotFoundException e) {
            throw new InvalidExtensionException(info.main() + ".class not found", e);
        }

        if (!parent.isAssignableFrom(mainClass)) {
            throw new InvalidExtensionException(mainClass, "does not extend", parent);
        }

        final Constructor<? extends T> declaredConstructor = mainClass.asSubclass(parent).getDeclaredConstructor(String.class, String.class, String.class, File.class, SimpleLogger.class);

        declaredConstructor.setAccessible(true);

        this.extension = declaredConstructor.newInstance(info.name(), info.version(), info.description(), rootDir, new SimpleLogger(mainClass));
    }

    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        Class<?> result = this.classes.get(name);

        if (result == null) {
            final String path = name.replace('.', '/').concat(".class");
            final JarEntry entry = jar.getJarEntry(path);

            if (entry != null) {
                final byte[] classBytes;

                try (InputStream inputStream = jar.getInputStream(entry)) {
                    classBytes = inputStream.readAllBytes();
                } catch (IOException ex) {
                    throw new ClassNotFoundException(name, ex);
                }

                final int dot = name.lastIndexOf('.');

                if (dot != -1) {
                    final String pkgName = name.substring(0, dot);

                    if (getDefinedPackage(pkgName) == null) {
                        try {
                            if (manifest != null) {
                                definePackage(pkgName, manifest, url);
                            } else {
                                definePackage(pkgName, null, null, null, null, null, null, null);
                            }
                        } catch (IllegalArgumentException ex) {
                            if (getDefinedPackage(pkgName) == null) {
                                throw new IllegalStateException("Cannot find package " + pkgName);
                            }
                        }
                    }
                }

                final CodeSigner[] signers = entry.getCodeSigners();
                final CodeSource source = new CodeSource(url, signers);
                result = defineClass(name, classBytes, 0, classBytes.length, source);

                if (result == null) result = super.findClass(name);

                classes.put(name, result);
            }
        }

        return result;
    }

    @Override
    public URL getResource(final String name) {
        return findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        return findResources(name);
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            jar.close();
        }
    }

    public T extension() {
        return extension;
    }
}
