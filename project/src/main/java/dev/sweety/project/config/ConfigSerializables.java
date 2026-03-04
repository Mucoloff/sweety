package dev.sweety.project.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigSerializables {

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


    public static <T extends ConfigSerializable> T construct(Class<T> clazz, Map<String, Object> data) {
        final SerializableConstructor constructor = SERIALIZABLES.computeIfAbsent(clazz, ConfigSerializables::create);
        try {
            return constructor.create(data);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private record SerializableConstructor(
            Class<? extends ConfigSerializable> clazz,
            Constructor<? extends ConfigSerializable> constructor
    ) {

        SerializableConstructor(Class<? extends ConfigSerializable> clazz) throws NoSuchMethodException {
            this(clazz, search(clazz));
        }

        private static Constructor<? extends ConfigSerializable> search(final Class<? extends ConfigSerializable> clazz) throws NoSuchMethodException {
            final List<Constructor<?>> constructors = Arrays.stream(clazz.getConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].equals(Map.class)
                    )
                    .toList();
            if (constructors.isEmpty())
                throw new NoSuchMethodException("Serializable " + clazz.getSimpleName() + " is missing (Map<String, Object>) constructor");

            // noinspection unchecked
            return (Constructor<? extends ConfigSerializable>) constructors.getFirst();
        }

        public <T extends ConfigSerializable> T create(Map<String, Object> data) throws InvocationTargetException, InstantiationException, IllegalAccessException {
            // noinspection unchecked
            return (T) this.constructor.newInstance(data);
        }

    }


}
