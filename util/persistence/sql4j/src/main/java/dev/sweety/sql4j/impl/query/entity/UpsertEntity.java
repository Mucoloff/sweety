package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.api.obj.InsertableColumns;
import dev.sweety.sql4j.api.query.MutationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.sweety.sql4j.api.query.UpsertQuery;

/*
 * Bug fix (Phase B): H2's MERGE INTO requires KEY columns to be in the VALUES list.
 * UpsertEntity excluded auto-increment PKs from the column list, causing H2 to throw
 * "Column contains null values" on the first upsert (null PK).
 *
 * Fix: H2Dialect.upsertSyntax() now includes PK columns in the column list. UpsertEntity
 * stores the PK columns separately and prepends them during bind().
 * When the auto-increment PK is null (first insert), UpsertEntity falls back to a plain
 * INSERT so the DB can generate the key — identical behaviour to insert().
 */
public final class UpsertEntity<T> extends AbstractQuery<MutationResult<T>> implements UpsertQuery<T> {

    private final Table<T> table;
    private final T instance;
    private final Metadata metadata;

    /**
     * @param pkColumnsForUpsert  PK columns that the dialect requires in the VALUES binding.
     *                            Empty for dialects that do NOT include PK in their upsert
     *                            column list (SQLite, MySQL, MariaDB, PostgreSQL).
     * @param insertFallbackSql   Plain INSERT SQL used when PK is null/zero (auto-increment
     *                            PKs cannot be used as MERGE keys while still unset).
     */
    private record Metadata(
            List<Column<?>> pkColumnsForUpsert,
            List<Column<?>> insertColumns,
            @Nullable Column<?> generatedColumn,
            String upsertSql,
            String insertFallbackSql) {}

    public UpsertEntity(Table<T> table, Dialect dialect, @NotNull T instance, QueryCache cache) {
        this.table = Objects.requireNonNull(table);
        this.instance = Objects.requireNonNull(instance);

        String cacheKey = "upsert:meta2:" + table.name() + ":" + table.clazz().getName() + ":" + dialect.name();
        this.metadata = cache.getMetadata(cacheKey, _ -> {
            InsertableColumns cols = table.insertableColumns();
            List<Column<?>> insertColumns = cols.columns();
            Column<?> generatedColumn = cols.autoIncrementColumn();

            List<String> insertColNames = insertColumns.stream().map(Column::name).collect(Collectors.toList());
            List<Column<?>> pkColumns = table.primaryKeys();
            List<String> pkColNames = pkColumns.stream().map(Column::name).collect(Collectors.toList());

            // Columns updated on conflict (everything that is not a PK)
            List<String> updateColNames = insertColumns.stream()
                    .map(Column::name)
                    .filter(name -> !pkColNames.contains(name))
                    .collect(Collectors.toList());

            if (pkColNames.isEmpty()) {
                throw new Sql4jMappingException("Table '" + table.name() + "' has no primary keys — cannot UPSERT.");
            }

            String upsertSql = dialect.upsertSyntax(table.name(), insertColNames, updateColNames, pkColNames);

            // Plain INSERT fallback — used when the auto-increment PK is still null
            String insertFallbackSql = "INSERT INTO " + table.toSql(dialect)
                    + " (" + insertColNames.stream().collect(Collectors.joining(", ")) + ")"
                    + " VALUES (" + insertColNames.stream().map(c -> "?").collect(Collectors.joining(", ")) + ")";

            // Determine whether the dialect's upsert SQL includes PK columns in the VALUES
            // binding (H2 MERGE INTO prepends them; others do not).
            List<Column<?>> pkColumnsForUpsert = isUpsertSqlIncludesPk(upsertSql, pkColNames)
                    ? pkColumns
                    : List.of();

            return new Metadata(pkColumnsForUpsert, insertColumns, generatedColumn, upsertSql, insertFallbackSql);
        });
    }

    /** Heuristic: if the generated upsert SQL lists PK columns before the INSERT columns. */
    private static boolean isUpsertSqlIncludesPk(String upsertSql, List<String> pkColNames) {
        if (pkColNames.isEmpty()) return false;
        // H2 MERGE prepends PK columns; check for their presence before the VALUES clause
        String upper = upsertSql.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("MERGE");
    }

    private UpsertEntity(Table<T> table, Metadata metadata, T instance) {
        this.table = table;
        this.metadata = metadata;
        this.instance = instance;
    }

    public UpsertEntity<T> copy(T instance) {
        return new UpsertEntity<>(table, metadata, instance);
    }

    private boolean isInsertFallback() {
        if (metadata.generatedColumn == null) return false;
        Object pkValue = metadata.generatedColumn.get(instance);
        return pkValue == null || (pkValue instanceof Number n && n.longValue() == 0);
    }

    @Override
    protected String buildSql() {
        return isInsertFallback() ? metadata.insertFallbackSql : metadata.upsertSql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        int idx = 1;
        if (isInsertFallback()) {
            // INSERT path: only bind non-PK (auto-increment) columns
            for (Column<?> c : metadata.insertColumns) c.set(ps, idx++, instance);
        } else {
            // UPSERT path: for dialects that include PK in the column list (H2 MERGE),
            // bind PK values first, then the remaining insert columns.
            for (Column<?> c : metadata.pkColumnsForUpsert) c.set(ps, idx++, instance);
            for (Column<?> c : metadata.insertColumns) c.set(ps, idx++, instance);
        }
    }

    @Override
    public MutationResult<T> execute(PreparedStatement ps) throws SQLException {
        int affected = ps.executeUpdate();
        int generatedId = affected;

        if (metadata.generatedColumn != null) {
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedId = rs.getInt(1);
                    metadata.generatedColumn.set(instance, generatedId);
                }
            } catch (SQLException ignore) {
                // Some drivers/dialects don't return generated keys on UPSERT when it updates
            }
        }
        return new MutationResult<>(generatedId, instance);
    }

    @Override
    public boolean returnGeneratedKeys() {
        return metadata.generatedColumn != null;
    }
}
