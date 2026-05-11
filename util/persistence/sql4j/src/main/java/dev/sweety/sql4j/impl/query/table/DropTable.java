package dev.sweety.sql4j.impl.query.table;

import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.schema.DropTableQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public final class DropTable extends AbstractQuery<Void> implements DropTableQuery {

    private final String sql;

    public DropTable(final String name) {
        this.sql = "DROP TABLE IF EXISTS " + Objects.requireNonNull(name, "name cannot be null");
    }

    public DropTable(final Table<?> table) {
        this(Objects.requireNonNull(table, "table cannot be null").name());
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {

    }

    @Override
    public Void execute(PreparedStatement ps) throws SQLException {
        ps.execute();
        return null;
    }
}
