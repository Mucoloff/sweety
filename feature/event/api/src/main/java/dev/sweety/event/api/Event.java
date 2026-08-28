package dev.sweety.event.api;

import dev.sweety.event.processor.GenerateEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Base interface for all events (Read-Only view).
 *
 * @param <E> The type of the event itself.
 */
public interface Event<E extends Event<E>> {

    @NotNull
    E post();

    boolean isPost();

    boolean isPre();

    boolean isChanged();

    static <T extends Event<T>> T of(Class<T> clazz, Object... args) {
        GenerateEvent annotation = clazz.getAnnotation(GenerateEvent.class);
        if (annotation == null) throw new IllegalStateException("No GenerateEvent annotation found for " + clazz);
        if (!annotation.type().immutable())
            throw new IllegalStateException("No immutable annotation found for " + clazz);
        return invokeStaticFactory(clazz, "of", args);
    }

    static <T extends Event<T>, R extends MutableEvent<T>> R ofMutable(Class<T> clazz, Object... args) {
        GenerateEvent annotation = clazz.getAnnotation(GenerateEvent.class);
        if (annotation == null) throw new IllegalStateException("No GenerateEvent annotation found for " + clazz);
        if (!annotation.type().mutable()) throw new IllegalStateException("No mutable annotation found for " + clazz);
        //noinspection unchecked
        return (R) invokeStaticFactory(clazz, "ofMutable", args);
    }

    Map<Class<?>, Map<String, Method>> construct = new HashMap<>();

    private static <T extends Event<T>> T invokeStaticFactory(Class<?> clazz, String methodName, Object... args) {
        // KSP-generated factory: <Interface>Factory (preferred)
        Class<?> search = clazz;
        try {
            search = Class.forName(clazz.getName() + "Factory", true, clazz.getClassLoader());
        } catch (ClassNotFoundException ignored) {
        }
        var cl = search;
        try {
            var me = construct.computeIfAbsent(search, ignored -> new HashMap<>(2))
                    .computeIfAbsent(methodName, name -> {
                        for (var m : cl.getDeclaredMethods()) {
                            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(name) && m.getParameterCount() == args.length) {
                                return m;
                            }
                        }
                        return null;
                    });

            if (me == null)
                throw new NoSuchMethodException("No suitable '" + methodName + "' found in " + search.getName());

            //noinspection unchecked
            return (T) me.invoke(null, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}