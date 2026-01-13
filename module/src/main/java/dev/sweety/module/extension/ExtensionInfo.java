package dev.sweety.module.extension;

import dev.sweety.core.config.GsonUtils;

import java.io.File;
import java.io.InputStreamReader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public record ExtensionInfo(String name, String version, String main) {

    static final ExtensionInfo BASE = new ExtensionInfo("name", "version", "mainClass");

    public static ExtensionInfo get(final File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            final JarEntry entry = jar.getJarEntry(Extension.NAME + ".json");

            if (entry == null) throw new ExtensionNotFoundException(Extension.NAME, file.getPath(), GsonUtils.gson().toJson(BASE, ExtensionInfo.class));

            try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry))) {
                return GsonUtils.gson().fromJson(reader, ExtensionInfo.class);
            }
        }
    }

}