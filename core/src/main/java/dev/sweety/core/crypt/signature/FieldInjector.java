package dev.sweety.core.crypt.signature;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;

import java.lang.reflect.Field;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public final class FieldInjector {

    public static byte[] inject(byte[] originalClass, Map<String, Object> fields) {
        ClassReader cr = new ClassReader(originalClass);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = new ClassVisitor(ASM9, cw) {

            @Override
            public FieldVisitor visitField(
                    final int access,
                    final String name,
                    final String descriptor,
                    final String signatureField,
                    Object defaultValue
            ) {
                Object value = (access & ACC_STATIC) != 0 && (access & ACC_FINAL) != 0 ? fields.getOrDefault(name, defaultValue) : defaultValue;
                return super.visitField(access, name, descriptor, signatureField, value);
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    public static <T> T readField(Class<?> clazz, String fieldName) {
        try {
            final Field field = clazz.getDeclaredField(fieldName);
            if (!field.canAccess(null)) field.setAccessible(true);
            //noinspection unchecked
            return ((T) field.get(null));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
