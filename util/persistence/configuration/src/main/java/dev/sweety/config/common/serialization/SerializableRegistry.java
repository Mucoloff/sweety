package dev.sweety.config.common.serialization;

import dev.sweety.config.common.ConfigurationSection;
import dev.sweety.serialization.Reader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class SerializableRegistry {

    private static final Map<Class<? extends ConfigSerializable>, SerializableConstructor> SERIALIZABLES = new HashMap<>();

    public static void register(Class<? extends ConfigSerializable> clazz) {
        if (!SERIALIZABLES.containsKey(clazz)) SERIALIZABLES.put(clazz, create(clazz));
    }

    private static SerializableConstructor create(Class<? extends ConfigSerializable> clazz) {
        try {
            return new SerializableConstructor(clazz);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a {@link Reader} that deserializes a {@code T} from a {@link ConfigSource}.
     * The reader calls {@link #construct} with the source's root map, capturing the reflective
     * constructor or factory lookup once at first use.
     */
    public static <T extends ConfigSerializable> Reader<T, ConfigSource> readerFor(Class<T> clazz) {
        register(clazz);
        return source -> construct(clazz, source.toMap());
    }

    public static <T extends ConfigSerializable> T construct(Class<T> clazz, Map<String, Object> data) {
        final SerializableConstructor constructor = SERIALIZABLES.computeIfAbsent(clazz, SerializableRegistry::create);
        try {
            return constructor.create(data);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private record SerializableConstructor(
            Class<? extends ConfigSerializable> clazz,
            Executable executable
    ) {

        SerializableConstructor(Class<? extends ConfigSerializable> clazz) throws NoSuchMethodException {
            this(clazz, search(clazz));
        }

        private static Executable search(final Class<? extends ConfigSerializable> clazz) throws NoSuchMethodException {

            final Predicate<Executable> condition = exec -> {
                if (exec.getParameterCount() != 1) return false;
                var type = exec.getParameterTypes()[0];
                return type.equals(ConfigurationSection.class);
            };

            final List<Constructor<?>> constructors = Arrays.stream(clazz.getConstructors())
                    .filter(condition)
                    .toList();

            final List<Method> methods = Arrays.stream(clazz.getMethods())
                    .filter(method -> condition.test(method) && method.getReturnType().equals(clazz) && (method.getModifiers() & Modifier.STATIC) != 0)
                    .toList();

            if (constructors.isEmpty() && methods.isEmpty())
                throw new NoSuchMethodException("No constructor or static method found for class " + clazz.getName());

            if (constructors.size() + methods.size() > 1)
                throw new NoSuchMethodException("Multiple constructors or static methods found for class " + clazz.getName());

            final Executable executable = methods.isEmpty() ? constructors.getFirst() : methods.getFirst();
            if (!executable.trySetAccessible()) executable.setAccessible(true);
            return executable;

        }

        public <T extends ConfigSerializable> T create(Map<String, Object> data) throws InvocationTargetException, InstantiationException, IllegalAccessException {
            final Object argument = executable.getParameterTypes()[0].equals(Map.class) ? data : ConfigurationSection.fromMap(data);
            return switch (this.executable) {
                case Constructor<?> constructor ->
                    //noinspection unchecked
                        (T) constructor.newInstance(argument);
                case Method method ->
                    //noinspection unchecked
                        (T) method.invoke(null, argument);
                case null ->
                        throw new IllegalStateException("Executable must be either a constructor or a static method");
            };
        }

    }

}
