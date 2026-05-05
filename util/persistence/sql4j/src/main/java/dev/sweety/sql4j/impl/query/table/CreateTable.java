package dev.sweety.sql4j.impl.query.table;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.ForeignKey;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public final class CreateTable extends AbstractQuery<Void> {

    private final String sql;

    public CreateTable(Table<?> table, Dialect dialect, boolean ifNotExists) {
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(dialect, "dialect cannot be null");
        this.sql = build(table, dialect, ifNotExists);
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    public java.util.List<String> buildAllSql(Table<?> table, Dialect dialect, boolean ifNotExists) {
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add(sql);
        list.addAll(buildIndices(table, ifNotExists));
        return list;
    }

    @Override
    public void bind(PreparedStatement ps) {
        // nothing to bind
    }

    @Override
    public Void execute(PreparedStatement ps) throws SQLException {
        ps.execute();
        return null;
    }

    private static String build(Table<?> table, Dialect dialect, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ");
        if (ifNotExists && dialect.supportsIfNotExists()) sb.append("IF NOT EXISTS ");
        sb.append(table.name()).append(" (");

        StringJoiner cols = new StringJoiner(", ");
        boolean isSinglePK = table.primaryKeys().size() == 1;
        boolean compositePK = table.primaryKeys().size() > 1;

        for (Column c : table.columns()) {
            // Case: single PK + autoIncrement — inline definition only (no separate constraint)
            if (c.isPrimaryKey() && c.isAutoIncrement() && isSinglePK) {
                cols.add(c.name() + " INTEGER PRIMARY KEY " + dialect.autoIncrement());
                continue;
            }

            if (c.isAutoIncrement()) {
                // autoIncrement on a non-single-PK column is unsupported
                throw new IllegalStateException(
                        "AUTOINCREMENT can only be used on a single INTEGER PRIMARY KEY column. " +
                        "Offending column: '" + c.name() + "' in table '" + table.name() + "'");
            }

            StringBuilder col = new StringBuilder();
            col.append(c.name()).append(" ").append(dialect.sqlType(c.type()));

            // NOT NULL unless explicitly nullable
            if (!c.isNullable() && !c.isPrimaryKey()) {
                col.append(" NOT NULL");
            }
            
            if (c.defaultValue() != null && !c.defaultValue().isEmpty()) {
                col.append(" DEFAULT ").append(c.defaultValue());
            }

            if (c.isUnique() && isSinglePK && !c.isPrimaryKey()) {
                col.append(" UNIQUE");
            }

            cols.add(col.toString());
        }

        sb.append(cols);

        // Separate PRIMARY KEY constraint for:
        // - composite PKs
        // - single PK that is NOT autoIncrement
        if (compositePK || (isSinglePK && !table.primaryKeys().getFirst().isAutoIncrement())) {
            sb.append(", PRIMARY KEY (")
                    .append(table.primaryKeys().stream().map(Column::name).collect(Collectors.joining(", ")))
                    .append(")");
        }

        // FK constraints
        if (dialect.supportsForeignKeys()) {
            for (ForeignKey fk : table.foreignKeys()) {
                sb.append(", FOREIGN KEY (").append(fk.local().name()).append(")")
                        .append(" REFERENCES ").append(fk.referencedTable().name())
                        .append("(").append(fk.referencedColumn().name()).append(")")
                        .append(" ON DELETE ").append(dialect.foreignKeyAction(fk.onDelete()))
                        .append(" ON UPDATE ").append(dialect.foreignKeyAction(fk.onUpdate()));
            }
        }

        sb.append(")");
        return sb.toString();
    }

    public static java.util.List<String> buildIndices(Table<?> table, boolean ifNotExists) {
        java.util.List<String> indices = new java.util.ArrayList<>();
        for (Column c : table.columns()) {
            if (c.indexName() != null) {
                StringBuilder sb = new StringBuilder("CREATE ");
                if (c.isUnique()) sb.append("UNIQUE ");
                sb.append("INDEX ");
                if (ifNotExists) sb.append("IF NOT EXISTS ");
                sb.append(c.indexName()).append(" ON ").append(table.name())
                        .append("(").append(c.name()).append(")");
                indices.add(sb.toString());
            }
        }
        return indices;
    }
}
