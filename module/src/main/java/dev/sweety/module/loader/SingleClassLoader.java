package dev.sweety.module.loader;

import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SingleClassLoader extends ClassLoader {

    public SingleClassLoader() {
        super();
    }

    public SingleClassLoader(ClassLoader parent) {
        super(parent);
    }

    public SingleClassLoader(String name, ClassLoader parent) {
        super(name, parent);
    }

    public Class<?> loadClassFromFile(File classFile) throws IOException {
        return loadClassFromFile(classFile, getClassName(classFile));
    }

    public Class<?> loadClassFromFile(File classFile, String className) throws IOException {
        byte[] classData;
        try (FileInputStream fis = new FileInputStream(classFile)) {
            classData = fis.readAllBytes();
        }
        return defineClass(className, classData, 0, classData.length);
    }

    public static String getClassName(File classFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(classFile)) {
            ClassReader reader = new ClassReader(fis);
            return reader.getClassName().replace('/', '.');
        }
    }

}

