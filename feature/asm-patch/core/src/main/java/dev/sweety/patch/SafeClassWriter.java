package dev.sweety.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class SafeClassWriter extends ClassWriter {

    private final ClassLoader classLoader;

    public SafeClassWriter(int flags) {
        this(null, flags, Thread.currentThread().getContextClassLoader());
    }

    public SafeClassWriter(ClassReader reader, int flags) {
        this(reader, flags, Thread.currentThread().getContextClassLoader());
    }

    public SafeClassWriter(ClassReader reader, int flags, ClassLoader classLoader) {
        super(reader, flags);
        this.classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
            return "java/lang/Object";
        }
        try {
            Class<?> class1 = Class.forName(type1.replace('/', '.'), false, classLoader);
            Class<?> class2 = Class.forName(type2.replace('/', '.'), false, classLoader);

            if (class1.isAssignableFrom(class2)) {
                return type1;
            }
            if (class2.isAssignableFrom(class1)) {
                return type2;
            }
            if (class1.isInterface() || class2.isInterface()) {
                return "java/lang/Object";
            }
            do {
                class1 = class1.getSuperclass();
                if (class1 == null) return "java/lang/Object";
            } while (!class1.isAssignableFrom(class2));

            return class1.getName().replace('.', '/');
        } catch (Throwable t) {
            // Fallback gracefully without throwing ClassNotFoundException
            return "java/lang/Object";
        }
    }
}
