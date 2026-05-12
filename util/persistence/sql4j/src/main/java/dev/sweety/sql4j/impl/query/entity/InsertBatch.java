package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.exception.Sql4jQueryException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.BatchQuery;
import dev.sweety.sql4j.impl.query.QueryCache;
import org.jetbrains.annotations.Nullable;

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

public final class InsertBatch<T> extends AbstractQuery<int[]> implements BatchQuery<T> {

    private final Table<T> table;
    private final Collection<T> instances;
    private final Metadata metadata;
    /** 0 = no chunking; positive = split into chunks of this size. */
    private final int chunkSize;

    private record Metadata(List<Column<?>> insertColumns, @Nullable Column<?> generatedColumn, String sql) {}

    public InsertBatch(Table<T> table, dev.sweety.sql4j.api.connection.dialect.Dialect dialect,
                       Collection<T> instances, QueryCache cache) {
        this(table, dialect, instances, cache, 0);
    }

    public InsertBatch(Table<T> table, dev.sweety.sql4j.api.connection.dialect.Dialect dialect,
                       Collection<T> instances, QueryCache cache, int chunkSize) {
        this.table = Objects.requireNonNull(table, "table is null");
        this.instances = Objects.requireNonNull(instances, "instances is null");
        this.chunkSize = chunkSize;
        Objects.requireNonNull(cache, "cache is null");
        Objects.requireNonNull(table.insertableColumns(), "table.insertableColumns() is null for " + table.name());

        if (instances.isEmpty()) {
            throw new Sql4jQueryException("Cannot insert an empty batch");
        }

        T firstInstance = instances.iterator().next();
        List<Column<?>> allInsertable = table.insertableColumns().columns();
        List<Column<?>> activeColumns = new java.util.ArrayList<>();
        for (Column<?> c : allInsertable) {
            Object val = c.get(firstInstance);
            if (val == null && c.defaultValue() != null && !c.defaultValue().isEmpty()) {
                continue;
            }
            activeColumns.add(c);
        }

        String colKey = activeColumns.stream().map(Column::name).sorted().collect(Collectors.joining(","));
        String cacheKey = "insertBatch:meta:" + table.name() + ":" + colKey + ":" + dialect.name();

        this.metadata = cache.getMetadata(cacheKey, _ -> {
            Column<?> generatedColumn = table.insertableColumns().autoIncrementColumn();
            int fieldsPerRow = activeColumns.size();

            String colNames = activeColumns.stream().map(c -> c.toSql(dialect)).collect(Collectors.joining(", "));
            String placeholders = "(" + "?,".repeat(fieldsPerRow).replaceAll(",$", "") + ")";
            String sql = "INSERT INTO " + table.toSql(dialect) + " (" + colNames + ") VALUES " + placeholders;

            return new Metadata(activeColumns, generatedColumn, sql);
        });
    }

    // ─── Standard (non-chunked) path ────────────────────────────────────────────

    @Override
    protected String buildSql() { return metadata.sql; }

    @Override
    public boolean returnGeneratedKeys() { return false; }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        for (T instance : instances) {
            int i = 1;
            for (Column c : metadata.insertColumns) {
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
     * Internal constructor used by the chunked execution path to create sub-batches that
     * reuse the already-computed {@link Metadata} instead of rebuilding it via a new
     * (empty) {@link QueryCache} per chunk.
     */
    private InsertBatch(Table<T> table, List<T> instances, Metadata metadata) {
        this.table = table;
        this.instances = instances;
        this.metadata = metadata;
        this.chunkSize = 0;
    }

    // ─── Chunked execution path (overrides Query default) ───────────────────────

    /**
     * When {@code chunkSize > 0} and the batch is larger than one chunk, splits the
     * collection into sub-lists and executes each sub-list as a separate batch on the
     * same JDBC connection, avoiding oversized single batches.
     * The {@link Metadata} (SQL + column list) is computed once and shared across all chunks.
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
                    // O4: reuse parent metadata — avoids new QueryCache() + metadata rebuild
                    InsertBatch<T> sub = new InsertBatch<>(table, chunk, this.metadata);
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
