package dev.sweety.project;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileConfiguration {

    private final Yaml yaml = new Yaml();

    public void save(File file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(yaml.dumpAsMap(map).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            map.clear();
            map.putAll(yaml.loadAs(fis, Map.class));
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
        return getString(path, def != null ? def.toString() : null);
    }


    @Contract("_, !null -> !null")
    @Nullable
    public String getString(@NotNull String path, @Nullable String def) {
        Object val = get(path);
        return (val != null) ? val.toString() : def;
    }


    public boolean isString(@NotNull String path) {
        Object val = get(path);
        return val instanceof String;
    }


    public int getInt(@NotNull String path) {
        Object def = get(path);
        return getInt(path, (def instanceof Number n) ? n.intValue() : 0);
    }


    public int getInt(@NotNull String path, int def) {
        Object val = get(path);
        return (val instanceof Number n) ? n.intValue() : def;
    }


    public boolean isInt(@NotNull String path) {
        Object val = get(path);
        return val instanceof Integer;
    }


    public boolean getBoolean(@NotNull String path) {
        Object def = get(path);
        return getBoolean(path, (def instanceof Boolean) ? (Boolean) def : false);
    }


    public boolean getBoolean(@NotNull String path, boolean def) {
        Object val = get(path);
        return (val instanceof Boolean) ? (Boolean) val : def;
    }


    public boolean isBoolean(@NotNull String path) {
        Object val = get(path);
        return val instanceof Boolean;
    }


    public double getDouble(@NotNull String path) {
        Object def = get(path);
        return getDouble(path, (def instanceof Number n) ? n.doubleValue() : 0);
    }


    public double getDouble(@NotNull String path, double def) {
        Object val = get(path);
        return (val instanceof Number n) ? n.doubleValue() : def;
    }


    public boolean isDouble(@NotNull String path) {
        Object val = get(path);
        return val instanceof Double;
    }


    public long getLong(@NotNull String path) {
        Object def = get(path);
        return getLong(path, (def instanceof Number n) ? n.longValue() : 0);
    }


    public long getLong(@NotNull String path, long def) {
        Object val = get(path);
        return (val instanceof Number n) ? n.longValue() : def;
    }


    public boolean isLong(@NotNull String path) {
        Object val = get(path);
        return val instanceof Long;
    }

    // Java

    @Nullable
    public List<?> getList(@NotNull String path) {
        Object def = get(path);
        return getList(path, (def instanceof List) ? (List<?>) def : null);
    }


    @Contract("_, !null -> !null")
    @Nullable
    public List<?> getList(@NotNull String path, @Nullable List<?> def) {
        Object val = get(path);
        return (List<?>) ((val instanceof List) ? val : def);
    }


    public boolean isList(@NotNull String path) {
        Object val = get(path);
        return val instanceof List;
    }


    @NotNull
    public List<String> getStringList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<String>(0);
        }

        List<String> result = new ArrayList<String>();

        for (Object object : list) {
            if ((object instanceof String) || (isPrimitiveWrapper(object))) {
                result.add(String.valueOf(object));
            }
        }

        return result;
    }


    @NotNull
    public List<Integer> getIntegerList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Integer>(0);
        }

        List<Integer> result = new ArrayList<Integer>();

        for (Object object : list) {
            if (object instanceof Integer) {
                result.add((Integer) object);
            } else if (object instanceof String) {
                try {
                    result.add(Integer.valueOf((String) object));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character) {
                result.add((int) ((Character) object).charValue());
            } else if (object instanceof Number) {
                result.add(((Number) object).intValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Boolean> getBooleanList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Boolean>(0);
        }

        List<Boolean> result = new ArrayList<Boolean>();

        for (Object object : list) {
            if (object instanceof Boolean) {
                result.add((Boolean) object);
            } else if (object instanceof String) {
                if (Boolean.TRUE.toString().equals(object)) {
                    result.add(true);
                } else if (Boolean.FALSE.toString().equals(object)) {
                    result.add(false);
                }
            }
        }

        return result;
    }


    @NotNull
    public List<Double> getDoubleList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Double>(0);
        }

        List<Double> result = new ArrayList<Double>();

        for (Object object : list) {
            if (object instanceof Double) {
                result.add((Double) object);
            } else if (object instanceof String) {
                try {
                    result.add(Double.valueOf((String) object));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character) {
                result.add((double) ((Character) object).charValue());
            } else if (object instanceof Number) {
                result.add(((Number) object).doubleValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Float> getFloatList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Float>(0);
        }

        List<Float> result = new ArrayList<Float>();

        for (Object object : list) {
            if (object instanceof Float) {
                result.add((Float) object);
            } else if (object instanceof String) {
                try {
                    result.add(Float.valueOf((String) object));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character) {
                result.add((float) ((Character) object).charValue());
            } else if (object instanceof Number) {
                result.add(((Number) object).floatValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Long> getLongList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Long>(0);
        }

        List<Long> result = new ArrayList<Long>();

        for (Object object : list) {
            if (object instanceof Long) {
                result.add((Long) object);
            } else if (object instanceof String) {
                try {
                    result.add(Long.valueOf((String) object));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character) {
                result.add((long) ((Character) object).charValue());
            } else if (object instanceof Number) {
                result.add(((Number) object).longValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Byte> getByteList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Byte>(0);
        }

        List<Byte> result = new ArrayList<Byte>();

        for (Object object : list) {
            if (object instanceof Byte) {
                result.add((Byte) object);
            } else if (object instanceof String) {
                try {
                    result.add(Byte.valueOf((String) object));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character) {
                result.add((byte) ((Character) object).charValue());
            } else if (object instanceof Number) {
                result.add(((Number) object).byteValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Character> getCharacterList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Character>(0);
        }

        List<Character> result = new ArrayList<Character>();

        for (Object object : list) {
            if (object instanceof Character) {
                result.add((Character) object);
            } else if (object instanceof String) {
                String str = (String) object;

                if (str.length() == 1) {
                    result.add(str.charAt(0));
                }
            } else if (object instanceof Number) {
                result.add((char) ((Number) object).intValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Short> getShortList(@NotNull String path) {
        List<?> list = getList(path);

        if (list == null) {
            return new ArrayList<Short>(0);
        }

        List<Short> result = new ArrayList<Short>();

        for (Object object : list) {
            if (object instanceof Short) {
                result.add((Short) object);
            } else if (object instanceof String) {
                try {
                    result.add(Short.valueOf((String) object));
                } catch (Exception ex) {
                }
            } else if (object instanceof Character) {
                result.add((short) ((Character) object).charValue());
            } else if (object instanceof Number) {
                result.add(((Number) object).shortValue());
            }
        }

        return result;
    }


    @NotNull
    public List<Map<?, ?>> getMapList(@NotNull String path) {
        List<?> list = getList(path);
        List<Map<?, ?>> result = new ArrayList<Map<?, ?>>();

        if (list == null) {
            return result;
        }

        for (Object object : list) {
            if (object instanceof Map) {
                result.add((Map<?, ?>) object);
            }
        }

        return result;
    }

    // Bukkit
    @Nullable

    public <T extends Object> T getObject(@NotNull String path, @NotNull Class<T> clazz) {
        Preconditions.checkArgument(clazz != null, "Class cannot be null");
        Object def = get(path);
        return getObject(path, clazz, (def != null && clazz.isInstance(def)) ? clazz.cast(def) : null);
    }

    @Contract("_, _, !null -> !null")
    @Nullable

    public <T extends Object> T getObject(@NotNull String path, @NotNull Class<T> clazz, @Nullable T def) {
        Preconditions.checkArgument(clazz != null, "Class cannot be null");
        Object val = get(path);
        return (val != null && clazz.isInstance(val)) ? clazz.cast(val) : def;
    }

    public boolean contains(String path) {
        return get(path) != null;
    }

    /* =======================
       SETTERS
       ======================= */

    public void set(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);

            if (!(next instanceof Map)) {
                Map<String, Object> newSection = new HashMap<>();
                current.put(parts[i], newSection);
                current = newSection;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) next;
                current = casted;
            }
        }

        current.put(parts[parts.length - 1], value);
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
        Object val = get(path);
        return (val instanceof Map) ? (Map<String, Object>) val : null;
    }
}
