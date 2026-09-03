package dev.sweety.sql4j.document;

import dev.sweety.config.common.ConfigurationSection;
import dev.sweety.config.json.GsonUtils;
import dev.sweety.config.yml.YamlUtils;
import dev.sweety.sql4j.api.connection.SqlConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Robust relational-backed implementation of {@link DocumentCollection}.
 */
public final class DocumentCollectionImpl<T, ID> implements DocumentCollection<T, ID> {

    private final String name;
    private final String tableName;
    private final DocumentCodec<T> codec;
    private final SqlConnection sqlConnection;
    private final Class<ID> idClass;

    public DocumentCollectionImpl(String name, Class<T> targetClass, DocumentFormat format, Class<ID> idClass, SqlConnection sqlConnection) {
        this.name = Objects.requireNonNull(name, "name");
        this.tableName = "_doc_" + name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        this.codec = DocumentCodec.of(targetClass, format);
        this.idClass = Objects.requireNonNull(idClass, "idClass");
        this.sqlConnection = Objects.requireNonNull(sqlConnection, "sqlConnection");
        initTable();
    }

    private void initTable() {
        String textType = switch (sqlConnection.dialectType()) {
            case POSTGRESQL, SQLITE, H2 -> "TEXT";
            case MYSQL, MARIADB -> "LONGTEXT";
        };

        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id VARCHAR(255) PRIMARY KEY, " +
                "format VARCHAR(16) NOT NULL, " +
                "content " + textType + ", " +
                "updated_at BIGINT NOT NULL" +
                ")";

        try (Connection con = sqlConnection.connection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize document collection table: " + tableName, e);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DocumentFormat format() {
        return codec.format();
    }

    @Override
    public void put(ID id, T document) {
        Objects.requireNonNull(id, "id");
        if (document == null) {
            delete(id);
            return;
        }

        String serialized = codec.serialize(document);
        long now = System.currentTimeMillis();

        String sql = switch (sqlConnection.dialectType()) {
            case SQLITE -> "INSERT INTO " + tableName + " (id, format, content, updated_at) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(id) DO UPDATE SET format=excluded.format, content=excluded.content, updated_at=excluded.updated_at";
            case POSTGRESQL -> "INSERT INTO " + tableName + " (id, format, content, updated_at) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (id) DO UPDATE SET format = EXCLUDED.format, content = EXCLUDED.content, updated_at = EXCLUDED.updated_at";
            case H2 -> "MERGE INTO " + tableName + " (id, format, content, updated_at) KEY(id) VALUES (?, ?, ?, ?)";
            case MYSQL, MARIADB -> "INSERT INTO " + tableName + " (id, format, content, updated_at) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE format=VALUES(format), content=VALUES(content), updated_at=VALUES(updated_at)";
        };

        try (Connection con = sqlConnection.connection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            ps.setString(2, codec.format().name());
            ps.setString(3, serialized);
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to put document into " + tableName, e);
        }
    }

    @Override
    public CompletableFuture<Void> putAsync(ID id, T document) {
        return CompletableFuture.runAsync(() -> put(id, document), sqlConnection.executor());
    }

    @Override
    public Optional<T> get(ID id) {
        Objects.requireNonNull(id, "id");
        String sql = "SELECT content FROM " + tableName + " WHERE id = ?";

        try (Connection con = sqlConnection.connection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String content = rs.getString("content");
                    return Optional.ofNullable(codec.deserialize(content));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get document from " + tableName, e);
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Optional<T>> getAsync(ID id) {
        return CompletableFuture.supplyAsync(() -> get(id), sqlConnection.executor());
    }

    @Override
    public T computeIfAbsent(ID id, Function<ID, T> mappingFunction) {
        Optional<T> existing = get(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        T created = mappingFunction.apply(id);
        if (created != null) {
            put(id, created);
        }
        return created;
    }

    @Override
    public boolean delete(ID id) {
        Objects.requireNonNull(id, "id");
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";

        try (Connection con = sqlConnection.connection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete document from " + tableName, e);
        }
    }

    @Override
    public CompletableFuture<Boolean> deleteAsync(ID id) {
        return CompletableFuture.supplyAsync(() -> delete(id), sqlConnection.executor());
    }

    @Override
    public boolean exists(ID id) {
        Objects.requireNonNull(id, "id");
        String sql = "SELECT 1 FROM " + tableName + " WHERE id = ?";

        try (Connection con = sqlConnection.connection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existence in " + tableName, e);
        }
    }

    @Override
    public List<T> findAll() {
        String sql = "SELECT content FROM " + tableName + " ORDER BY updated_at DESC";
        List<T> list = new ArrayList<>();

        try (Connection con = sqlConnection.connection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String content = rs.getString("content");
                T doc = codec.deserialize(content);
                if (doc != null) {
                    list.add(doc);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to findAll in " + tableName, e);
        }
        return list;
    }

    @Override
    public Map<ID, T> findAllMap() {
        String sql = "SELECT id, content FROM " + tableName;
        Map<ID, T> map = new LinkedHashMap<>();

        try (Connection con = sqlConnection.connection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String rawId = rs.getString("id");
                String content = rs.getString("content");
                ID id = convertId(rawId);
                T doc = codec.deserialize(content);
                if (id != null && doc != null) {
                    map.put(id, doc);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to findAllMap in " + tableName, e);
        }
        return map;
    }

    @Override
    public List<T> find(Predicate<T> filter) {
        return findAll().stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Connection con = sqlConnection.connection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count documents in " + tableName, e);
        }
        return 0;
    }

    @Override
    public void clear() {
        String sql = "DELETE FROM " + tableName;
        try (Connection con = sqlConnection.connection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear " + tableName, e);
        }
    }

    @Override
    public void exportToFile(Path destination) throws IOException {
        Map<ID, T> map = findAllMap();
        Map<String, Object> rawMap = new LinkedHashMap<>();
        for (Map.Entry<ID, T> entry : map.entrySet()) {
            T val = entry.getValue();
            if (val instanceof ConfigurationSection section) {
                rawMap.put(String.valueOf(entry.getKey()), section.toMap());
            } else {
                rawMap.put(String.valueOf(entry.getKey()), val);
            }
        }

        String exported = (codec.format() == DocumentFormat.YAML)
                ? YamlUtils.write(rawMap)
                : GsonUtils.write(rawMap);

        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Files.writeString(destination, exported, StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void importFromFile(Path source) throws IOException {
        if (!Files.exists(source)) return;
        String content = Files.readString(source, StandardCharsets.UTF_8);
        Map<String, Object> rawMap = (codec.format() == DocumentFormat.YAML)
                ? YamlUtils.read(content, (Class<Map<String, Object>>) (Class<?>) Map.class)
                : GsonUtils.read(content, (Class<Map<String, Object>>) (Class<?>) Map.class);

        if (rawMap != null) {
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                ID id = convertId(entry.getKey());
                Object val = entry.getValue();
                String serializedVal = (codec.format() == DocumentFormat.YAML)
                        ? YamlUtils.write(val)
                        : GsonUtils.write(val);
                T doc = codec.deserialize(serializedVal);
                if (id != null && doc != null) {
                    put(id, doc);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ID convertId(String rawId) {
        if (idClass == String.class) return (ID) rawId;
        if (idClass == UUID.class) return (ID) UUID.fromString(rawId);
        if (idClass == Long.class) return (ID) Long.valueOf(rawId);
        if (idClass == Integer.class) return (ID) Integer.valueOf(rawId);
        return (ID) rawId;
    }
}
