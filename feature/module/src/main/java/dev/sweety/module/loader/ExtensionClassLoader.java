package dev.sweety.module.loader;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.module.extension.Extension;
import dev.sweety.module.extension.ExtensionInfo;
import lombok.Getter;

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

public class ExtensionClassLoader extends URLClassLoader {

    private final JarFile jar;
    private final Manifest manifest;
    private final URL url;
    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
    @Getter
    private final Extension extension;

    public ExtensionClassLoader(final File jarFile, final ExtensionInfo info, final ClassLoader parent, File rootDir) throws Exception {
        super(new URL[]{jarFile.toURI().toURL()}, parent);
        this.jar = new JarFile(jarFile);
        this.manifest = jar.getManifest();
        this.url = jarFile.toURI().toURL();

        final Class<?> mainClass = Class.forName(info.main(), true, this);

        if (!Extension.class.isAssignableFrom(mainClass)) {
            throw new RuntimeException(mainClass.getName() + " does not extend " + Extension.class.getSimpleName());
        }

        final Constructor<? extends Extension> declaredConstructor = mainClass.asSubclass(Extension.class).getDeclaredConstructor(String.class, File.class, SimpleLogger.class);

        declaredConstructor.setAccessible(true);

        this.extension = declaredConstructor.newInstance(info.name(), rootDir, new SimpleLogger(mainClass));
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
}
