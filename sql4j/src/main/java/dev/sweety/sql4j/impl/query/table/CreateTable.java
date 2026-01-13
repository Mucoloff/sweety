package dev.sweety.sql4j.impl.query.table;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.StringJoiner;

public final class CreateTable extends AbstractQuery<Void> {

    private final String sql;

    public CreateTable(Table<?> table, Dialect dialect, boolean ifNotExists) {
        this.sql = build(table, dialect, ifNotExists);
        System.out.println("create table sql: " + this.sql);
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

        if (ifNotExists && dialect.supportsIfNotExists())
            sb.append("IF NOT EXISTS ");

        sb.append(table.name()).append(" (");

        StringJoiner cols = new StringJoiner(", ");
        Column pk = null;

        for (Column c : table.columns()) {
            StringBuilder col = new StringBuilder();
            col.append(c.name()).append(" ");
            col.append(dialect.sqlType(c.field().getType()));

            boolean inlinePk =
                    c.isPrimaryKey()
                            && c.isAutoIncrement()
                            && dialect.inlinePrimaryKeyForAutoIncrement();

            if (inlinePk) {
                col.append(" PRIMARY KEY ");
                col.append(dialect.autoIncrement());
            } else {
                if (c.isAutoIncrement())
                    col.append(" ").append(dialect.autoIncrement());

                if (c.isPrimaryKey()) {
                    if (pk != null)
                        throw new IllegalStateException("Multiple primary keys not supported");
                    pk = c;
                }
            }

            cols.add(col.toString());
        }

        sb.append(cols);

        // PK separata solo se NON già inline
        if (pk != null && !dialect.inlinePrimaryKeyForAutoIncrement()) {
            sb.append(", PRIMARY KEY (").append(pk.name()).append(")");
        }

        sb.append(")");
        return sb.toString();
    }

}
