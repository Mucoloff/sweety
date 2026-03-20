package dev.sweety.config.common;

import dev.sweety.config.common.serialization.ConfigSerializable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Configuration {

    protected abstract String dumpAsMap(Map<String, Object> map);

    protected abstract Map<String, Object> loadAsMap(Reader reader);

    public void save(Appendable writer) {
        try {
            writer.append(dumpAsMap(map));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(Reader reader) {
        map.clear();
        this.map.putAll(loadAsMap(reader));
    }

    public void load(File file) {
        try (FileReader reader = new FileReader(file)) {
            load(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            save(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<String, Object> map = new HashMap<>();


    /* =======================
       GETTERS
       ======================= */


    @Nullable
    public String getString(@NotNull String path) {
        Object def = get(path);
        return getString(path, (def != null) ? (def instanceof String s ? s : def.toString()) : null);
    }


    @Contract("_, !null -> !null")
    @Nullable
    public String getString(@NotNull String path, @Nullable String def) {
        Object val = get(path);
        return (val != null) ? (val instanceof String s ? s : val.toString()) : def;
    }


    public boolean isString(@NotNull String path) {
        return get(path) instanceof String;
    }


    public int getInt(@NotNull String path) {
        return getInt(path, (get(path) instanceof Number n) ? n.intValue() : 0);
    }


    public int getInt(@NotNull String path, int def) {
        return (get(path) instanceof Number n) ? n.intValue() : def;
    }


    public boolean isInt(@NotNull String path) {
        return get(path) instanceof Integer;
    }


    public boolean getBoolean(@NotNull String path) {
        return getBoolean(path, (get(path) instanceof Boolean b) ? b : false);
    }


    public boolean getBoolean(@NotNull String path, boolean def) {
        return (get(path) instanceof Boolean b) ? b : def;
    }


    public boolean isBoolean(@NotNull String path) {
        return get(path) instanceof Boolean;
    }


    public double getDouble(@NotNull String path) {
        return getDouble(path, (get(path) instanceof Number n) ? n.doubleValue() : 0);
    }


    public double getDouble(@NotNull String path, double def) {
        return (get(path) instanceof Number n) ? n.doubleValue() : def;
    }


    public boolean isDouble(@NotNull String path) {
        return get(path) instanceof Double;
    }

    public float getFloat(@NotNull String path) {
        return getFloat(path, (get(path) instanceof Number n) ? n.floatValue() : 0);
    }


    public float getFloat(@NotNull String path, float def) {
        return (get(path) instanceof Number n) ? n.floatValue() : def;
    }


    public boolean isFloat(@NotNull String path) {
        return get(path) instanceof Float;
    }


    public long getLong(@NotNull String path) {
        return getLong(path, (get(path) instanceof Number n) ? n.longValue() : 0);
    }


    public long getLong(@NotNull String path, long def) {
        return (get(path) instanceof Number n) ? n.longValue() : def;
    }


    public boolean isLong(@NotNull String path) {
        return get(path) instanceof Long;
    }

    // Java

    @Nullable
    public List<?> getList(@NotNull String path) {
        return getList(path, (get(path) instanceof List<?> l) ? l : null);
    }


    @Contract("_, !null -> !null")
    @Nullable
    public List<?> getList(@NotNull String path, @Nullable List<?> def) {
        return (get(path) instanceof List<?> l) ? l : def;
    }


    public boolean isList(@NotNull String path) {
        return get(path) instanceof List;
    }


    @NotNull
    public List<String> getStringList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<String> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof String s) {
                result.add(s);
            } else if (isPrimitiveWrapper(object)) {
                result.add(String.valueOf(object));
            }
        }

        return result;
    }


    @NotNull
    public List<Integer> getIntegerList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Integer> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Integer i) {
                result.add(i);
            } else if (object instanceof String s) {
                try {
                    result.add(Integer.valueOf(s));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character c) {
                result.add((int) c);
            } else if (object instanceof Number n) {
                result.add(n.intValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Boolean> getBooleanList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Boolean> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Boolean b) {
                result.add(b);
            } else if (object instanceof String s) {
                if (Boolean.TRUE.toString().equals(s)) {
                    result.add(true);
                } else if (Boolean.FALSE.toString().equals(s)) {
                    result.add(false);
                }
            }
        }

        return result;
    }


    @NotNull
    public List<Double> getDoubleList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Double> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Double d) {
                result.add(d);
            } else if (object instanceof String s) {
                try {
                    result.add(Double.valueOf(s));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character c) {
                result.add((double) c);
            } else if (object instanceof Number n) {
                result.add(n.doubleValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Float> getFloatList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Float> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Float f) {
                result.add(f);
            } else if (object instanceof String f) {
                try {
                    result.add(Float.valueOf(f));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character c) {
                result.add((float) c);
            } else if (object instanceof Number n) {
                result.add(n.floatValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Long> getLongList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Long> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Long l) {
                result.add(l);
            } else if (object instanceof String s) {
                try {
                    result.add(Long.valueOf(s));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character c) {
                result.add((long) c);
            } else if (object instanceof Number n) {
                result.add(n.longValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Byte> getByteList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Byte> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Byte b) {
                result.add(b);
            } else if (object instanceof String s) {
                try {
                    result.add(Byte.valueOf(s));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character c) {
                result.add((byte) c.charValue());
            } else if (object instanceof Number n) {
                result.add(n.byteValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Character> getCharacterList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Character> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Character c) {
                result.add(c);
            } else if (object instanceof String str) {

                if (str.length() == 1) {
                    result.add(str.charAt(0));
                }
            } else if (object instanceof Number n) {
                result.add((char) n.intValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Short> getShortList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) return new ArrayList<>(0);

        List<Short> result = new ArrayList<>(list.size());

        for (Object object : list) {
            if (object instanceof Short s) {
                result.add(s);
            } else if (object instanceof String s) {
                try {
                    result.add(Short.valueOf(s));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character c) {
                result.add((short) c.charValue());
            } else if (object instanceof Number n) {
                result.add(n.shortValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Map<?, ?>> getMapList(@NotNull String path) {
        final List<?> list = getList(path);
        if (list == null) return new ArrayList<>(0);

        List<Map<?, ?>> result = new ArrayList<>(list.size());
        for (Object object : list) {
            if (object instanceof Map<?, ?> m) {
                result.add(m);
            }
        }

        return result;
    }

    @NotNull
    public <T extends ConfigSerializable> Map<String, T> getSerializableMap(@NotNull String path, @NotNull Class<T> clazz) {
        final Map<String, Object> map = getMap(path);
        if (map == null) return new HashMap<>(0);

        final Map<String, T> result = new HashMap<>(map.size());

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            //noinspection unchecked
            result.put(entry.getKey(), dev.sweety.config.common.serialization.SerializableRegistry.construct(clazz, (Map<String, Object>) entry.getValue()));
        }

        return result;
    }

    @NotNull
    public <T extends ConfigSerializable> List<T> getSerializableList(@NotNull String path, @NotNull Class<T> clazz) {
        final List<Map<?, ?>> list = getMapList(path);
        if (list.isEmpty()) return new ArrayList<>(0);

        final List<T> result = new ArrayList<>(list.size());
        for (Map<?, ?> map : list) {
            //noinspection unchecked
            result.add(dev.sweety.config.common.serialization.SerializableRegistry.construct(clazz, (Map<String, Object>) map));
        }
        return result;
    }

    // Bukkit
    @Nullable
    public <T> T getObject(@NotNull String path, @NotNull Class<T> clazz) {
        Object def = get(path);
        return getObject(path, clazz, (clazz.isInstance(def)) ? clazz.cast(def) : null);
    }

    @Contract("_, _, !null -> !null")
    @Nullable

    public <T> T getObject(@NotNull String path, @NotNull Class<T> clazz, @Nullable T def) {
        Object val = get(path);
        return (clazz.isInstance(val)) ? clazz.cast(val) : def;
    }

    public boolean contains(String path) {
        return get(path) != null;
    }

    /* =======================
       SETTERS
       ======================= */

    public void set(String path, Object value) {
        final String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            final Object next = current.get(parts[i]);

            if (next instanceof Map<?, ?> m) {
                //noinspection unchecked
                current = (Map<String, Object>) m;
            } else {
                final Map<String, Object> newSection = new HashMap<>();
                current.put(parts[i], newSection);
                current = newSection;
            }
        }

        final Object val;
        if (value instanceof ConfigSerializable serializable) {
            dev.sweety.config.common.serialization.SerializableRegistry.register(serializable.getClass());
            val = serializable.serialize();
        } else if (value instanceof List<?> l) {
            val = serializeList(l);
        } else if (value instanceof Map<?, ?> m) {
            val = serializeMap(m);
        } else val = value;

        current.put(parts[parts.length - 1], val);
    }

    private List<?> serializeList(List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            switch (item) {
                case ConfigSerializable serializable -> {
                    dev.sweety.config.common.serialization.SerializableRegistry.register(serializable.getClass());
                    result.add(serializable.serialize());
                }
                case List<?> l -> result.add(serializeList(l));
                case Map<?, ?> m -> result.add(serializeMap(m));
                case null, default -> result.add(item);
            }
        }
        return result;
    }

    private Map<String, Object> serializeMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            Object serializedValue;

            switch (value) {
                case ConfigSerializable serializable -> {
                    dev.sweety.config.common.serialization.SerializableRegistry.register(serializable.getClass());
                    serializedValue = serializable.serialize();
                }
                case List<?> l -> serializedValue = serializeList(l);
                case Map<?, ?> m -> serializedValue = serializeMap(m);
                case null, default -> serializedValue = value;
            }

            result.put(entry.getKey().toString(), serializedValue);
        }
        return result;
    }

    protected boolean isPrimitiveWrapper(@Nullable Object input) {
        return input instanceof Integer || input instanceof Boolean
                || input instanceof Character || input instanceof Byte
                || input instanceof Short || input instanceof Double
                || input instanceof Long || input instanceof Float;
    }

    /* =======================
       INTERNAL
       ======================= */

    @SuppressWarnings("unchecked")
    private Object get(String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length; i++) {
            Object value = current.get(parts[i]);

            if (value == null) return null;
            if (i == parts.length - 1) return value;

            if (!(value instanceof Map)) return null;
            current = (Map<String, Object>) value;
        }
        return null;
    }

    public Map<String, Object> getMap(String path) {
        final Object val = get(path);
        //noinspection unchecked
        return (val instanceof Map<?, ?> m) ? (Map<String, Object>) m : null;
    }

    public <T extends ConfigSerializable> T getSerializable(String path, Class<T> clazz) {
        return dev.sweety.config.common.serialization.SerializableRegistry.construct(clazz, getMap(path));
    }
}
