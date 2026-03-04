package dev.sweety.core.config.yml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.sweety.core.config.GsonUtils;
import dev.sweety.core.config.adapters.GsonAdapter;

import java.lang.reflect.*;
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
            Executable executable
    ) {

        SerializableConstructor(Class<? extends ConfigSerializable> clazz) throws NoSuchMethodException {
            this(clazz, search(clazz));
        }

        private static Executable search(final Class<? extends ConfigSerializable> clazz) throws NoSuchMethodException {
            final List<Constructor<?>> constructors = Arrays.stream(clazz.getConstructors())
                    .filter(constructor -> constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].equals(Map.class)
                    )
                    .toList();

            final List<Method> methods = Arrays.stream(clazz.getMethods())
                    .filter(method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(Map.class) && method.getReturnType().equals(clazz) && (method.getModifiers() & Modifier.STATIC) != 0)
                    .toList();

            if (constructors.isEmpty() && methods.isEmpty()) {
                throw new NoSuchMethodException("No constructor or static method found for class " + clazz.getName());
            }

            if (constructors.size() + methods.size() > 1) {
                throw new NoSuchMethodException("Multiple constructors or static methods found for class " + clazz.getName());
            }

            final Executable executable = methods.isEmpty() ? constructors.getFirst() : methods.getFirst();
            if (!executable.trySetAccessible()) executable.setAccessible(true);
            return executable;

        }

        public <T extends ConfigSerializable> T create(Map<String, Object> data) throws InvocationTargetException, InstantiationException, IllegalAccessException {
            return switch (this.executable) {
                case Constructor<?> constructor ->
                    //noinspection unchecked
                        (T) constructor.newInstance(data);
                case Method method ->
                    //noinspection unchecked
                        (T) method.invoke(null, data);
                case null ->
                        throw new IllegalStateException("Executable must be either a constructor or a static method");
            };
        }

    }


}
