package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.exception.Sql4jQueryException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.BatchQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public final class UpdateBatch<T> extends AbstractQuery<int[]> implements BatchQuery<T> {

    private final Table<T> table;
    private final Collection<T> instances;
    private final Metadata metadata;
    /** 0 = no chunking; positive = split into chunks of this size. */
    private final int chunkSize;

    private record Metadata(List<Column<?>> updateColumns, List<Column<?>> primaryKeys, String sql) {}

    public UpdateBatch(Table<T> table, Dialect dialect,
                       Collection<T> instances, QueryCache cache) {
        this(table, dialect, instances, cache, 0);
    }

    public UpdateBatch(Table<T> table, Dialect dialect,
                       Collection<T> instances, QueryCache cache, int chunkSize) {
        this.table = Objects.requireNonNull(table, "table is null");
        this.instances = Objects.requireNonNull(instances, "instances is null");
        this.chunkSize = chunkSize;
        Objects.requireNonNull(cache, "cache is null");

        if (instances.isEmpty()) {
            throw new Sql4jQueryException("Cannot update an empty batch");
        }

        String cacheKey = "updateBatch:meta:" + table.name() + ":" + table.clazz().getName() + ":" + dialect.name();
        this.metadata = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> primaryKeys = table.primaryKeys();
            List<Column<?>> updateColumns = table.updatableColumns();

            String setClause = updateColumns.stream()
                    .map(c -> c.toSql(dialect) + "=?")
                    .collect(Collectors.joining(", "));

            String whereClause = primaryKeys.stream()
                    .map(c -> c.toSql(dialect) + "=?")
                    .collect(Collectors.joining(" AND "));

            String sql = "UPDATE " + table.toSql(dialect) + " SET " + setClause + " WHERE " + whereClause;
            return new Metadata(updateColumns, primaryKeys, sql);
        });
    }

    // ─── Standard (non-chunked) path ────────────────────────────────────────────

    @Override
    protected String buildSql() { return metadata.sql; }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        for (T instance : instances) {
            int i = 1;
            for (Column c : metadata.updateColumns) {
                ps.setObject(i++, c.get(instance));
            }
            for (Column c : metadata.primaryKeys) {
                ps.setObject(i++, c.get(instance));
            }
            ps.addBatch();
        }
    }

    @Override
    public int[] execute(PreparedStatement ps) throws SQLException {
        return ps.executeBatch();
    }

    /**
     * Internal constructor for chunk sub-batches: reuses the already-computed
     * {@link Metadata} instead of rebuilding it via a new (empty) {@link QueryCache}.
     */
    private UpdateBatch(Table<T> table, List<T> instances, Metadata metadata) {
        this.table = table;
        this.instances = instances;
        this.metadata = metadata;
        this.chunkSize = 0;
    }

    // ─── Chunked execution path (overrides Query default) ───────────────────────

    /**
     * When {@code chunkSize > 0} and the batch is larger than one chunk, splits the
     * collection into sub-lists. The {@link Metadata} (SQL + column lists) is computed
     * once and shared across all chunks (O4).
     */
    @Override
    public CompletableFuture<int[]> execute(final SqlConnection connection) {
        if (chunkSize <= 0 || instances.size() <= chunkSize) {
            return connection.executeAsync(this);
        }
        return CompletableFuture.supplyAsync(() -> {
            List<int[]> parts = new ArrayList<>();
            List<List<T>> chunks = partition(new ArrayList<>(instances), chunkSize);
            try (Connection rawCon = connection.connection()) {
                for (List<T> chunk : chunks) {
                    // O4: reuse parent metadata — no new QueryCache() + metadata rebuild per chunk
                    UpdateBatch<T> sub = new UpdateBatch<>(table, chunk, this.metadata);
                    try (PreparedStatement ps = rawCon.prepareStatement(sub.buildSql())) {
                        sub.bind(ps);
                        parts.add(ps.executeBatch());
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return merge(parts);
        }, connection.executor());
    }

    private static <E> List<List<E>> partition(List<E> list, int size) {
        List<List<E>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private static int[] merge(List<int[]> parts) {
        int total = parts.stream().mapToInt(a -> a.length).sum();
        int[] merged = new int[total];
        int idx = 0;
        for (int[] part : parts) {
            System.arraycopy(part, 0, merged, idx, part.length);
            idx += part.length;
        }
        return merged;
    }
}
