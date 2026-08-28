package dev.sweety.config.common;

import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.config.common.serialization.SerializableRegistry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class Configuration implements ConfigurationSection {

    private static final Object NOT_CACHED = new Object();
    private static final Object MISS = new Object();

    private final ConfigNode root = new ConfigNode();
    private final Map<String, Object> cache = new HashMap<>();
    private final Map<String, ConfigNode> nodeCache = new HashMap<>();

    private final String extension;

    public Configuration(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    protected abstract void dumpToStream(Map<String, Object> map, OutputStream out) throws IOException;

    protected abstract Map<String, Object> loadFromStream(InputStream in) throws IOException;

    @Override
    public String path() {
        return "";
    }

    public void save(OutputStream out) {
        try {
            dumpToStream(toMap(root), out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(InputStream in) {
        try {
            root.children.clear();
            cache.clear();
            nodeCache.clear();
            fromMap(root, loadFromStream(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes to a sibling temp file, then atomically renames it over {@code path} — a crash/kill mid-write
     * leaves the temp file torn, never the real config, so the next {@link #load(Path)} always sees either
     * the old file intact or the fully-written new one, never a partial write.
     */
    public void save(Path path) {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE))) {
            save(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // some filesystems (network mounts, certain Windows setups) can't do an atomic rename
                // across the two files even on the same directory — fall back to a plain (non-atomic)
                // replace rather than losing the write entirely.
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(Path path) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path,
                StandardOpenOption.READ))) {
            load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 👉 opzionale compatibilità testo
    public void save(Writer writer) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            save(baos);
            writer.write(baos.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(Reader reader) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            reader.transferTo(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
            load(new ByteArrayInputStream(baos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* =======================
       SETTERS / INTERNAL WRITE
       ======================= */

    @Override
    public void set(String path, Object value) {
        String pathDot = path + ".";
        cache.entrySet().removeIf(e -> e.getKey().equals(path) || e.getKey().startsWith(pathDot));
        nodeCache.entrySet().removeIf(e -> {
            String k = e.getKey();
            return k.equals(path) || k.startsWith(pathDot) || (e.getValue() == null && path.startsWith(k + "."));
        });
        if (value instanceof ConfigSerializable s) {
            ConfigNode node = traverseToNode(path);
            if (node != null) {
                node.children.clear();
                node.value = null;
            }
            ConfigurationSection section = getSection(path);
            if (section == null) {
                section = new MemoryConfigurationSection(this, path);
            }
            s.serialize(section);
            return;
        }
        ConfigNode node = root;
        int start = 0;
        while (true) {
            int dot = path.indexOf('.', start);
            String segment = dot < 0 ? path.substring(start) : path.substring(start, dot);
            if (dot < 0) {
                ConfigNode leaf = node.children.computeIfAbsent(segment, k -> new ConfigNode());
                Object val = serializeValue(value);
                if (val instanceof Map<?, ?> m) {
                    leaf.children.clear();
                    leaf.value = null;
                    fromMap(leaf, m);
                } else {
                    leaf.value = val;
                }
                break;
            }
            node = node.children.computeIfAbsent(segment, k -> new ConfigNode());
            start = dot + 1;
        }
    }

    private Map<String, Object> serializeSerializable(ConfigSerializable s) {
        Map<String, Object> map = new TreeMap<>();
        s.serialize(new MapConfigurationSection(map));
        return map;
    }

    private Object serializeValue(Object value) {
        return switch (value) {
            case ConfigSerializable s -> {
                yield serializeSerializable(s);
            }
            case List<?> l -> serializeList(l);
            case Map<?, ?> m -> serializeMap(m);
            case null, default -> value;
        };
    }

    private List<?> serializeList(List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            switch (item) {
                case ConfigSerializable s -> {
                    result.add(serializeSerializable(s));
                }
                case List<?> l -> result.add(serializeList(l));
                case Map<?, ?> m -> result.add(serializeMap(m));
                case null, default -> result.add(item);
            }
        }
        return result;
    }

    private Map<String, Object> serializeMap(Map<?, ?> map) {
        Map<String, Object> result = new TreeMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(entry.getKey().toString(), serializeValue(entry.getValue()));
        }
        return result;
    }

    /* =======================
       INTERNAL READ
       ======================= */

    @Override
    public Object get(String path) {
        Object cached = cache.getOrDefault(path, NOT_CACHED);
        if (cached != NOT_CACHED) return cached == MISS ? null : cached;
        ConfigNode node = traverseToNode(path);
        Object result = (node != null && node.hasValue()) ? node.value : null;
        cache.put(path, result != null ? result : MISS);
        return result;
    }

    /**
     * Navigates the tree following dot-separated segments; returns the final node or null if absent.
     * Caches each intermediate node so sibling/descendant lookups skip already-visited segments.
     */
    private ConfigNode traverseToNode(String path) {
        if (path.isEmpty()) return root;
        if (nodeCache.containsKey(path)) return nodeCache.get(path);
        ConfigNode node = root;
        int start = 0;
        while (true) {
            int dot = path.indexOf('.', start);
            String subPath = dot < 0 ? path : path.substring(0, dot);
            String segment = dot < 0 ? path.substring(start) : path.substring(start, dot);

            if (nodeCache.containsKey(subPath)) {
                node = nodeCache.get(subPath);
                if (node == null) {
                    nodeCache.put(path, null);
                    return null;
                }
            } else {
                node = node.children.get(segment);
                if (node == null) {
                    nodeCache.put(path, null);
                    return null;
                }
                nodeCache.put(subPath, node);
            }

            if (dot < 0) return node;
            start = dot + 1;
        }
    }

    @Override
    @Nullable
    public ConfigurationSection getSection(@NotNull String path) {
        return contains(path) ? new MemoryConfigurationSection(this, path) : null;
    }

    @Contract("_, !null -> !null")
    @Nullable
    public ConfigurationSection getSection(@NotNull String path, @Nullable ConfigurationSection def) {
        ConfigurationSection section = getSection(path);
        return section != null ? section : def;
    }

    public static final class MemoryConfigurationSection implements ConfigurationSection {
        private final Configuration root;
        private final String prefix;

        MemoryConfigurationSection(Configuration root, String prefix) {
            this.root = root;
            this.prefix = prefix;
        }

        @Override
        public String path() {
            return prefix;
        }

        @Override
        public String path(String path) {
            return prefix + "." + path;
        }

        @Override
        public Object get(String path) {
            return root.get(path(path));
        }

        @Override
        public boolean contains(String path) {
            return root.contains(path(path));
        }

        @Override
        public void set(String path, Object value) {
            root.set(path(path), value);
        }

        @Override
        @Nullable
        public Map<String, Object> getMap(String path) {
            return root.getMap(path(path));
        }

        @Override
        @Nullable
        public ConfigurationSection getSection(String path) {
            return root.getSection(path(path));
        }
    }

    @Override
    public boolean contains(String path) {
        ConfigNode node = traverseToNode(path);
        return node != null && (node.hasValue() || node.hasChildren());
    }

    @Override
    public Map<String, Object> getMap(String path) {
        ConfigNode node = traverseToNode(path);
        return (node != null && node.hasChildren()) ? toMap(node) : null;
    }

    public Map<String, Object> flatten() {
        Map<String, Object> out = new TreeMap<>();
        flattenInto(root, "", out);
        return out;
    }

    /**
     * Populates {@code node} from a nested map loaded by {@link #loadFromStream}.
     */
    private static void fromMap(ConfigNode node, Map<?, ?> src) {
        if (src == null) return;   // empty document → loadFromStream returns null
        for (Map.Entry<?, ?> e : src.entrySet()) {
            String key = e.getKey().toString();
            ConfigNode child = node.children.computeIfAbsent(key, k -> new ConfigNode());
            if (e.getValue() instanceof Map<?, ?> m) {
                fromMap(child, m);
            } else {
                child.value = e.getValue();
            }
        }
    }

    /**
     * Converts the tree rooted at {@code node} back to a nested TreeMap for serialization.
     */
    private static Map<String, Object> toMap(ConfigNode node) {
        Map<String, Object> result = new TreeMap<>();
        for (Map.Entry<String, ConfigNode> e : node.children.entrySet()) {
            ConfigNode child = e.getValue();
            result.put(e.getKey(), child.hasChildren() ? toMap(child) : child.value);
        }
        return result;
    }

    private static void flattenInto(ConfigNode node, String prefix, Map<String, Object> out) {
        for (Map.Entry<String, ConfigNode> e : node.children.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            ConfigNode child = e.getValue();
            if (child.hasValue()) out.put(key, child.value);
            if (child.hasChildren()) flattenInto(child, key, out);
        }
    }

    /**
     * One node per path segment. Leaf nodes carry a value; section nodes carry children.
     */
    private static final class ConfigNode {
        final HashMap<String, ConfigNode> children = new HashMap<>();
        Object value;

        boolean hasValue() {
            return value != null;
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }
    }
}
