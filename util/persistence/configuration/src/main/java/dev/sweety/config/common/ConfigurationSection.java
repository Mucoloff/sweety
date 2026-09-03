package dev.sweety.config.common;

import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.config.common.serialization.SerializableRegistry;
import dev.sweety.data.optional.OptionalBoolean;
import dev.sweety.data.optional.OptionalFloat;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatArrayMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.logging.Logger;

public interface ConfigurationSection {

    class LoggerHolder {
        private static final Logger LOG = Logger.getLogger(ConfigurationSection.class.getName());
    }

    String path();

    default String path(String path) {
        String p = path();
        return p == null || p.isEmpty() ? path : p + "." + path;
    }

    Object get(String path);

    boolean contains(String path);

    void set(String path, Object value);

    @Nullable
    Map<String, Object> getMap(String path);

    /**
     * Converts this configuration section into a standard Map representation.
     */
    default Map<String, Object> toMap() {
        Map<String, Object> map = getMap("");
        return map != null ? map : Map.of();
    }

    @Nullable
    ConfigurationSection getSection(String path);

    /* =======================
       GETTERS (default implementations)
       ======================= */

    @Nullable
    default String getString(@NotNull String path) {
        Object def = get(path);
        return getString(path, (def != null) ? (def instanceof String s ? s : def.toString()) : null);
    }

    @Contract("_, !null -> !null")
    @Nullable
    default String getString(@NotNull String path, @Nullable String def) {
        Object val = get(path);
        return (val != null) ? (val instanceof String s ? s : val.toString()) : def;
    }

    default Optional<String> getOptionalString(@NotNull String path) {
        Object val = get(path);
        if (val == null) return Optional.empty();
        return Optional.of(val instanceof String s ? s : val.toString());
    }

    default boolean isString(@NotNull String path) {
        return get(path) instanceof String;
    }

    default int getInt(@NotNull String path) {
        return getInt(path, (get(path) instanceof Number n) ? n.intValue() : 0);
    }

    default int getInt(@NotNull String path, int def) {
        return (get(path) instanceof Number n) ? n.intValue() : def;
    }

    default OptionalInt getIntOption(@NotNull String path) {
        return (get(path) instanceof Number n) ? OptionalInt.of(n.intValue()) : OptionalInt.empty();
    }

    default boolean isInt(@NotNull String path) {
        return get(path) instanceof Integer;
    }

    default boolean getBoolean(@NotNull String path) {
        return getBoolean(path, (get(path) instanceof Boolean b) ? b : false);
    }

    default boolean getBoolean(@NotNull String path, boolean def) {
        return (get(path) instanceof Boolean b) ? b : def;
    }

    default OptionalBoolean getBooleanOption(@NotNull String path) {
        return (get(path) instanceof Boolean b) ? OptionalBoolean.of(b) : OptionalBoolean.EMPTY;
    }

    default boolean isBoolean(@NotNull String path) {
        return get(path) instanceof Boolean;
    }

    default double getDouble(@NotNull String path) {
        return getDouble(path, (get(path) instanceof Number n) ? n.doubleValue() : 0);
    }

    default double getDouble(@NotNull String path, double def) {
        return (get(path) instanceof Number n) ? n.doubleValue() : def;
    }

    default OptionalDouble getDoubleOption(@NotNull String path) {
        return (get(path) instanceof Number n) ? OptionalDouble.of(n.doubleValue()) : OptionalDouble.empty();
    }

    default boolean isDouble(@NotNull String path) {
        return get(path) instanceof Double;
    }

    default float getFloat(@NotNull String path) {
        return getFloat(path, (get(path) instanceof Number n) ? n.floatValue() : 0);
    }

    default float getFloat(@NotNull String path, float def) {
        return (get(path) instanceof Number n) ? n.floatValue() : def;
    }

    default OptionalFloat getFloatOption(@NotNull String path) {
        return (get(path) instanceof Number n) ? OptionalFloat.of(n.floatValue()) : OptionalFloat.empty();
    }

    default boolean isFloat(@NotNull String path) {
        return get(path) instanceof Float;
    }

    default long getLong(@NotNull String path) {
        return getLong(path, (get(path) instanceof Number n) ? n.longValue() : 0);
    }

    default long getLong(@NotNull String path, long def) {
        return (get(path) instanceof Number n) ? n.longValue() : def;
    }

    default OptionalLong getLongOption(@NotNull String path) {
        return (get(path) instanceof Number n) ? OptionalLong.of(n.longValue()) : OptionalLong.empty();
    }

    default boolean isLong(@NotNull String path) {
        return get(path) instanceof Long;
    }

    @Nullable
    default List<?> getList(@NotNull String path) {
        return getList(path, (get(path) instanceof List<?> l) ? l : null);
    }

    @Contract("_, !null -> !null")
    @Nullable
    default List<?> getList(@NotNull String path, @Nullable List<?> def) {
        return (get(path) instanceof List<?> l) ? l : def;
    }

    default boolean isList(@NotNull String path) {
        return get(path) instanceof List;
    }

    // ── per-type element parsers (null = skip) ────────────────────────────────────

    private String toStr(Object o) {
        if (o instanceof String s) return s;
        if (isPrimitiveWrapper(o)) return String.valueOf(o);
        return null;
    }

    private static Integer toInt(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Character c) return (int) c;
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) return parseOrSkip(s, Integer::valueOf, "integer");
        return null;
    }

    private static Long toLong(Object o) {
        if (o instanceof Long l) return l;
        if (o instanceof Character c) return (long) c;
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) return parseOrSkip(s, Long::valueOf, "long");
        return null;
    }

    private static Double toDouble(Object o) {
        if (o instanceof Double d) return d;
        if (o instanceof Character c) return (double) c;
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) return parseOrSkip(s, Double::valueOf, "double");
        return null;
    }

    private static Float toFloat(Object o) {
        if (o instanceof Float f) return f;
        if (o instanceof Character c) return (float) c;
        if (o instanceof Number n) return n.floatValue();
        if (o instanceof String s) return parseOrSkip(s, Float::valueOf, "float");
        return null;
    }

    private static Byte toByte(Object o) {
        if (o instanceof Byte b) return b;
        if (o instanceof Character c) return (byte) c.charValue();
        if (o instanceof Number n) return n.byteValue();
        if (o instanceof String s) return parseOrSkip(s, Byte::valueOf, "byte");
        return null;
    }

    private static Short toShort(Object o) {
        if (o instanceof Short sh) return sh;
        if (o instanceof Character c) return (short) c.charValue();
        if (o instanceof Number n) return n.shortValue();
        if (o instanceof String s) return parseOrSkip(s, Short::valueOf, "short");
        return null;
    }

    private static Character toChar(Object o) {
        if (o instanceof Character c) return c;
        if (o instanceof String s) return s.length() == 1 ? s.charAt(0) : null;
        if (o instanceof Number n) return (char) n.intValue();
        return null;
    }

    private static Boolean toBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) {
            if (Boolean.TRUE.toString().equals(s)) return Boolean.TRUE;
            if (Boolean.FALSE.toString().equals(s)) return Boolean.FALSE;
        }
        return null;
    }

    private static <E> E parseOrSkip(String s, Function<String, E> parser, String typeName) {
        try {
            return parser.apply(s);
        } catch (Exception ex) {
            LoggerHolder.LOG.fine("Skipping non-" + typeName + " value in list: '" + s + "': " + ex.getMessage());
            return null;
        }
    }

    private <E, T extends List<E>> T mapList(@NotNull String path, IntFunction<T> factory, Function<Object, E> parser) {
        var list = getList(path);
        if (list == null) return factory.apply(0);

        var result = factory.apply(list.size());
        for (var object : list) {
            var element = parser.apply(object);
            if (element != null) result.add(element);
        }
        return result;
    }

    default <E> E[] mapArray(@NotNull String path, IntFunction<E[]> factory, Function<Object, E> parser) {
        var list = getList(path);
        if (list == null) return factory.apply(0);

        ArrayList<E> tmp = new ArrayList<>(list.size());
        for (var object : list) {
            var element = parser.apply(object);
            if (element != null) tmp.add(element);
        }
        return tmp.toArray(factory);
    }

    @NotNull
    default List<String> getStringList(@NotNull String path) {
        return mapList(path, ArrayList::new, this::toStr);
    }

    default <T extends List<Integer>> T getIntegerList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toInt);
    }

    @NotNull
    default List<Integer> getIntegerList(@NotNull String path) {
        return getIntegerList(path, ArrayList::new);
    }

    @NotNull
    default IntArrayList getIntArrayList(@NotNull String path) {
        return getIntegerList(path, IntArrayList::new);
    }

    default <T extends List<Boolean>> T getBooleanList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toBool);
    }

    @NotNull
    default List<Boolean> getBooleanList(@NotNull String path) {
        return getBooleanList(path, ArrayList::new);
    }

    @NotNull
    default BooleanArrayList getBooleanArrayList(@NotNull String path) {
        return getBooleanList(path, BooleanArrayList::new);
    }

    default <T extends List<Double>> T getDoubleList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toDouble);
    }

    @NotNull
    default List<Double> getDoubleList(@NotNull String path) {
        return getDoubleList(path, ArrayList::new);
    }

    @NotNull
    default DoubleArrayList getDoubleArrayList(@NotNull String path) {
        return getDoubleList(path, DoubleArrayList::new);
    }

    default <T extends List<Float>> T getFloatList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toFloat);
    }

    @NotNull
    default List<Float> getFloatList(@NotNull String path) {
        return getFloatList(path, ArrayList::new);
    }

    @NotNull
    default FloatArrayList getFloatArrayList(@NotNull String path) {
        return getFloatList(path, FloatArrayList::new);
    }

    default <T extends List<Long>> T getLongList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toLong);
    }

    @NotNull
    default List<Long> getLongList(@NotNull String path) {
        return getLongList(path, ArrayList::new);
    }

    @NotNull
    default LongArrayList getLongArrayList(@NotNull String path) {
        return getLongList(path, LongArrayList::new);
    }

    default <T extends List<Byte>> T getByteList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toByte);
    }

    @NotNull
    default List<Byte> getByteList(@NotNull String path) {
        return getByteList(path, ArrayList::new);
    }

    @NotNull
    default ByteArrayList getByteArrayList(@NotNull String path) {
        return getByteList(path, ByteArrayList::new);
    }

    default <T extends List<Character>> T getCharacterList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toChar);
    }

    @NotNull
    default List<Character> getCharacterList(@NotNull String path) {
        return getCharacterList(path, ArrayList::new);
    }

    @NotNull
    default CharArrayList getCharArrayList(@NotNull String path) {
        return getCharacterList(path, CharArrayList::new);
    }

    default <T extends List<Short>> T getShortList(@NotNull String path, IntFunction<T> factory) {
        return mapList(path, factory, ConfigurationSection::toShort);
    }

    @NotNull
    default List<Short> getShortList(@NotNull String path) {
        return getShortList(path, ArrayList::new);
    }

    @NotNull
    default ShortArrayList getShortArrayList(@NotNull String path) {
        return getShortList(path, ShortArrayList::new);
    }

    private <E, T extends Map<String, E>> T mapMap(@NotNull String path, IntFunction<T> factory, Function<Object, E> parser) {
        var map = getMap(path);
        if (map == null) return factory.apply(0);

        var result = factory.apply(map.size());
        for (var entry : map.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            var element = parser.apply(value);
            if (element != null) result.put(key, element);
        }
        return result;
    }

    default <T extends Map<String, Integer>> T getIntMap(@NotNull String path, IntFunction<T> factory) {
        return mapMap(path, factory, ConfigurationSection::toInt);
    }

    default Object2IntArrayMap<String> getIntArrayMap(@NotNull String path) {
        return getIntMap(path, Object2IntArrayMap::new);
    }

    default Object2IntOpenHashMap<String> getIntOpenHashMap(@NotNull String path) {
        return getIntMap(path, Object2IntOpenHashMap::new);
    }

    default <T extends Map<String, Long>> T getLongMap(@NotNull String path, IntFunction<T> factory) {
        return mapMap(path, factory, ConfigurationSection::toLong);
    }

    default Object2LongArrayMap<String> getLongArrayMap(@NotNull String path) {
        return getLongMap(path, Object2LongArrayMap::new);
    }

    default Object2LongOpenHashMap<String> getLongOpenHashMap(@NotNull String path) {
        return getLongMap(path, Object2LongOpenHashMap::new);
    }

    default <T extends Map<String, Float>> T getFloatMap(@NotNull String path, IntFunction<T> factory) {
        return mapMap(path, factory, ConfigurationSection::toFloat);
    }

    default Object2FloatArrayMap<String> getFloatArrayMap(@NotNull String path) {
        return getFloatMap(path, Object2FloatArrayMap::new);
    }

    default Object2FloatOpenHashMap<String> getFloatOpenHashMap(@NotNull String path) {
        return getFloatMap(path, Object2FloatOpenHashMap::new);
    }

    default <T extends Map<String, Double>> T getDoubleMap(@NotNull String path, IntFunction<T> factory) {
        return mapMap(path, factory, ConfigurationSection::toDouble);
    }

    default Object2DoubleArrayMap<String> getDoubleArrayMap(@NotNull String path) {
        return getDoubleMap(path, Object2DoubleArrayMap::new);
    }

    default Object2DoubleOpenHashMap<String> getDoubleOpenHashMap(@NotNull String path) {
        return getDoubleMap(path, Object2DoubleOpenHashMap::new);
    }

    @NotNull
    default List<Map<?, ?>> getMapList(@NotNull String path) {
        return mapList(path, ArrayList::new, o -> o instanceof Map<?, ?> m ? m : null);
    }

    @NotNull
    default <T extends ConfigSerializable> Map<String, T> getSerializableMap(@NotNull String path, @NotNull Class<T> clazz) {
        final Map<String, Object> map = getMap(path);
        if (map == null) return new TreeMap<>();

        final Map<String, T> result = new TreeMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            try {
                //noinspection unchecked
                result.put(entry.getKey(), SerializableRegistry.construct(clazz, (Map<String, Object>) entry.getValue()));
            } catch (RuntimeException e) {
                // One malformed entry (e.g. a schema change, a missing/renamed field) must not sink the
                // WHOLE map — that previously aborted the entire config's load() call one level up
                // (caught only at ConfigManager.load()'s per-CONFIG try/catch), silently discarding every
                // other entry too, and the next save() would then overwrite the file with that emptied
                // list — a load failure masquerading as "save doesn't work".
                LoggerHolder.LOG.warning("Skipping malformed " + clazz.getSimpleName() + " entry at key '"
                        + entry.getKey() + "' in '" + path + "': " + e.getMessage());
            }
        }
        return result;
    }

    @NotNull
    default <T extends ConfigSerializable> List<T> getSerializableList(@NotNull String path, @NotNull Class<T> clazz) {
        final List<Map<?, ?>> list = getMapList(path);
        if (list.isEmpty()) return new ArrayList<>(0);

        final List<T> result = new ArrayList<>(list.size());
        int i = 0;
        for (Map<?, ?> map : list) {
            try {
                //noinspection unchecked
                result.add(SerializableRegistry.construct(clazz, (Map<String, Object>) map));
            } catch (RuntimeException e) {
                // See getSerializableMap's comment — skip the bad row instead of losing the whole list.
                LoggerHolder.LOG.warning("Skipping malformed " + clazz.getSimpleName() + " entry #" + i
                        + " in '" + path + "': " + e.getMessage());
            }
            i++;
        }
        return result;
    }

    default byte getByte(@NotNull String path) {
        return getByte(path, (get(path) instanceof Number n) ? n.byteValue() : (byte) 0);
    }

    default byte getByte(@NotNull String path, byte def) {
        return (get(path) instanceof Number n) ? n.byteValue() : def;
    }

    default short getShort(@NotNull String path) {
        return getShort(path, (get(path) instanceof Number n) ? n.shortValue() : (short) 0);
    }

    default short getShort(@NotNull String path, short def) {
        return (get(path) instanceof Number n) ? n.shortValue() : def;
    }

    default char getChar(@NotNull String path) {
        return getChar(path, (char) 0);
    }

    default char getChar(@NotNull String path, char def) {
        Object val = get(path);
        if (val instanceof Character c) return c;
        if (val instanceof Number n) return (char) n.intValue();
        if (val instanceof String s && !s.isEmpty()) return s.charAt(0);
        return def;
    }

    @Nullable
    default UUID getUUID(@NotNull String path) {
        Object val = get(path);
        if (val instanceof UUID u) return u;
        if (val instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    default byte @Nullable [] getBytes(@NotNull String path) {
        Object val = get(path);
        return val instanceof byte[] b ? b : null;
    }

    @NotNull
    default <E extends Enum<E>> List<E> getEnumList(@NotNull String path, @NotNull Class<E> clazz) {
        return mapList(path, ArrayList::new, o -> {
            String name = o instanceof String s ? s : String.valueOf(o);
            try {
                return Enum.valueOf(clazz, name);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        });
    }

    @NotNull
    default <E extends Enum<E>> EnumSet<E> getEnumSet(@NotNull String path, @NotNull Class<E> clazz) {
        List<E> list = getEnumList(path, clazz);
        return list.isEmpty() ? EnumSet.noneOf(clazz) : EnumSet.copyOf(list);
    }

    @NotNull
    default List<UUID> getUUIDList(@NotNull String path) {
        return mapList(path, ArrayList::new, o -> {
            if (o instanceof UUID u) return u;
            try {
                return UUID.fromString(String.valueOf(o));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        });
    }

    @NotNull
    default <E> Set<E> getSet(@NotNull String path, Function<Object, E> parser) {
        var list = getList(path);
        if (list == null) return new LinkedHashSet<>(0);

        Set<E> result = new LinkedHashSet<>(list.size());
        for (var object : list) {
            var element = parser.apply(object);
            if (element != null) result.add(element);
        }
        return result;
    }

    default <E extends Enum<E>> E getEnum(@NotNull String path, @NotNull Class<E> clazz) {
        return getEnumOptional(path, clazz).orElseThrow(() -> new IllegalArgumentException("Missing or invalid enum value at path: " + path));
    }

    default <E extends Enum<E>> E getEnum(@NotNull String path, @NotNull E defaultValue) {
        //noinspection unchecked
        Optional<E> opt = getEnumOptional(path, defaultValue.getClass());
        return opt.orElse(defaultValue);
    }

    @NotNull
    default <E extends Enum<E>> Optional<E> getEnumOptional(@NotNull String path, @NotNull Class<E> clazz) {
        Object val = get(path);
        if (val == null) return Optional.empty();
        String name = val instanceof String s ? s : val.toString();
        try {
            return Optional.of(Enum.valueOf(clazz, name));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @NotNull
    default <T> Optional<T> getOptional(@NotNull String path, @NotNull Class<T> clazz) {
        Object val = get(path);
        return clazz.isInstance(val) ? Optional.of(clazz.cast(val)) : Optional.empty();
    }

    @Nullable
    default <T> T getObject(@NotNull String path, @NotNull Class<T> clazz) {
        Object def = get(path);
        return getObject(path, clazz, (clazz.isInstance(def)) ? clazz.cast(def) : null);
    }

    @Contract("_, _, !null -> !null")
    @Nullable
    default <T> T getObject(@NotNull String path, @NotNull Class<T> clazz, @Nullable T def) {
        Object val = get(path);
        return (clazz.isInstance(val)) ? clazz.cast(val) : def;
    }

    default <T extends ConfigSerializable> T getSerializable(@NotNull String path, @NotNull Class<T> clazz) {
        Map<String, Object> map = getMap(path);
        return map == null ? null : SerializableRegistry.construct(clazz, map);
    }

    default boolean isPrimitiveWrapper(@Nullable Object input) {
        return input instanceof Integer || input instanceof Boolean
                || input instanceof Character || input instanceof Byte
                || input instanceof Short || input instanceof Double
                || input instanceof Long || input instanceof Float;
    }

    static ConfigurationSection fromMap(Map<String, Object> map) {
        return new MapConfigurationSection(map);
    }

    default void writeToBuffer(dev.sweety.data.buffer.AbstractBuffer<?> buffer) {
        buffer.writeDynamic(toMap());
    }

    static ConfigurationSection fromBuffer(dev.sweety.data.buffer.AbstractBuffer<?> buffer) {
        Object obj = buffer.readDynamic();
        if (obj instanceof Map<?, ?> m) {
            Map<String, Object> map = new TreeMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
            return new MapConfigurationSection(map);
        }
        return new MapConfigurationSection(new TreeMap<>());
    }
}
