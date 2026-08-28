package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflective decoder registry for buffer-encoded types, mirroring how
 * {@code SerializableRegistry} discovers config deserialization for {@code ConfigSerializable}.
 *
 * <p>A type {@code T} is auto-discovered when it declares exactly one of:
 * <ol>
 *   <li>A public static field of type {@link AbstractCallableDecoder}{@code <T>}</li>
 *   <li>A public static no-arg method returning {@link AbstractCallableDecoder}{@code <T>}</li>
 *   <li>A public static method with a single {@link BufferReader} parameter returning {@code T}</li>
 *   <li>A public constructor with a single {@link BufferReader} parameter</li>
 * </ol>
 * Uniqueness is enforced across all four strategies combined.
 */
public final class BufferCodecRegistry {

    private static final Map<Class<?>, AbstractCallableDecoder<?>> DECODERS = new ConcurrentHashMap<>();

    private BufferCodecRegistry() {}

    /** Manually registers a decoder, bypassing reflective discovery. */
    public static <T> void register(Class<T> clazz, AbstractCallableDecoder<? extends T> decoder) {
        DECODERS.put(clazz, decoder);
    }

    /** Returns the decoder for {@code clazz}, discovering it via reflection on first call. */
    @SuppressWarnings("unchecked")
    public static <T> AbstractCallableDecoder<T> decoderFor(Class<T> clazz) {
        return (AbstractCallableDecoder<T>) DECODERS.computeIfAbsent(clazz, c -> {
            try {
                return discover((Class<T>) c);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Cannot discover decoder for " + c.getName(), e);
            }
        });
    }

    /** Convenience: decode a {@code T} directly from {@code reader}. */
    public static <T> T decode(Class<T> clazz, BufferReader reader) {
        return decoderFor(clazz).read(reader);
    }

    @SuppressWarnings("unchecked")
    private static <T> AbstractCallableDecoder<T> discover(Class<T> clazz)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        // 1. Static AbstractCallableDecoder fields
        List<Field> decoderFields = Arrays.stream(clazz.getFields())
                .filter(f -> Modifier.isStatic(f.getModifiers())
                        && AbstractCallableDecoder.class.isAssignableFrom(f.getType()))
                .toList();

        // 2. Static no-arg methods returning AbstractCallableDecoder
        List<Method> decoderMethods = Arrays.stream(clazz.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 0
                        && AbstractCallableDecoder.class.isAssignableFrom(m.getReturnType()))
                .toList();

        // 3. Static factory methods: static T x(BufferReader)
        List<Method> factoryMethods = Arrays.stream(clazz.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].equals(BufferReader.class)
                        && clazz.isAssignableFrom(m.getReturnType()))
                .toList();

        // 4. Constructors: T(BufferReader)
        List<Constructor<?>> constructors = Arrays.stream(clazz.getConstructors())
                .filter(c -> c.getParameterCount() == 1
                        && c.getParameterTypes()[0].equals(BufferReader.class))
                .toList();

        int total = decoderFields.size() + decoderMethods.size() + factoryMethods.size() + constructors.size();

        if (total == 0) {
            throw new NoSuchMethodException(
                    "No decoder found for " + clazz.getName() + ". Declare one of: " +
                    "a public static " + AbstractCallableDecoder.class.getSimpleName() + " field, " +
                    "a public static no-arg method returning " + AbstractCallableDecoder.class.getSimpleName() + ", " +
                    "a public static " + clazz.getSimpleName() + "(" + BufferReader.class.getSimpleName() + ") factory, " +
                    "or a public " + clazz.getSimpleName() + "(" + BufferReader.class.getSimpleName() + ") constructor.");
        }
        if (total > 1) {
            throw new NoSuchMethodException(
                    "Ambiguous decoder for " + clazz.getName() + ": " + total + " candidates found across all strategies.");
        }

        if (!decoderFields.isEmpty()) {
            Field f = decoderFields.get(0);
            if (!f.trySetAccessible()) f.setAccessible(true);
            return (AbstractCallableDecoder<T>) f.get(null);
        }

        if (!decoderMethods.isEmpty()) {
            Method m = decoderMethods.get(0);
            if (!m.trySetAccessible()) m.setAccessible(true);
            return (AbstractCallableDecoder<T>) m.invoke(null);
        }

        if (!factoryMethods.isEmpty()) {
            Method m = factoryMethods.get(0);
            if (!m.trySetAccessible()) m.setAccessible(true);
            return buf -> {
                try {
                    return (T) m.invoke(null, buf);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Factory invocation failed: " + m, e);
                }
            };
        }

        Constructor<T> ctor = (Constructor<T>) constructors.get(0);
        if (!ctor.trySetAccessible()) ctor.setAccessible(true);
        return buf -> {
            try {
                return ctor.newInstance(buf);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Constructor invocation failed: " + ctor, e);
            }
        };
    }
}
