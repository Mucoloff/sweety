package dev.sweety.sql4j.impl.query.table;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.ForeignKey;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public final class CreateTable extends AbstractQuery<Void> {

    private final String sql;

    public CreateTable(Table<?> table, Dialect dialect, boolean ifNotExists) {
        this.sql = build(table, dialect, ifNotExists);
    }

    @Override
    protected String buildSql() {
        return sql;
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
        boolean compositePK = table.primaryKeys().size() > 1;

        for (Column c : table.columns()) {
            StringBuilder col = new StringBuilder();
            col.append(c.name()).append(" ").append(dialect.sqlType(c.field().getType()));

            boolean isSinglePK = table.primaryKeys().size() == 1;

            if (c.isPrimaryKey() && c.isAutoIncrement() && isSinglePK) {
                // solo PK singola e tipo INTEGER
                col = new StringBuilder(c.name() + " INTEGER PRIMARY KEY " + dialect.autoIncrement());
            } else {
                // normale colonna
                if (c.isPrimaryKey() && !c.isAutoIncrement() && isSinglePK) {
                    col.append(" PRIMARY KEY");
                }
                if (c.isAutoIncrement()) {
                    // errore se non è INTEGER PK
                    throw new IllegalStateException("AUTOINCREMENT can only be used on a single INTEGER PRIMARY KEY: " + c.name()+ "\n: sql: " + sb.append(cols.add(col)) + " //interrupted");
                }
            }

            cols.add(col.toString());
        }

        sb.append(cols);

        // PK separata
        if (compositePK || (table.primaryKeys().size() == 1 && !table.primaryKeys().getFirst().isAutoIncrement())) {
            sb.append(", PRIMARY KEY (")
                    .append(table.primaryKeys().stream().map(Column::name).collect(Collectors.joining(", ")))
                    .append(")");
        }

        // FK
        for (ForeignKey fk : table.foreignKeys()) {
            sb.append(", FOREIGN KEY (").append(fk.local().name()).append(")")
                    .append(" REFERENCES ").append(fk.referencedTable().name())
                    .append("(").append(fk.referencedColumn().name()).append(")")
                    .append(" ON DELETE ").append(dialect.foreignKeyAction(fk.onDelete()))
                    .append(" ON UPDATE ").append(dialect.foreignKeyAction(fk.onUpdate()));
        }

        sb.append(")");
        return sb.toString();
    }


}
