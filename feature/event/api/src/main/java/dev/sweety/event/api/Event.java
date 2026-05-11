package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;

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
        return invokeStaticFactory(clazz, "of", args);
    }

    static <T extends Event<T>> T ofMutable(Class<T> clazz, Object... args) {
        return invokeStaticFactory(clazz, "ofMutable", args);
    }

    private static <T extends Event<T>> T invokeStaticFactory(Class<?> clazz, String methodName, Object... args) {
        try {
            for (var m : clazz.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    //noinspection unchecked
                    return (T) m.invoke(null, args);
                }
            }
            throw new NoSuchMethodException("No suitable '" + methodName + "' method found in " + clazz.getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}