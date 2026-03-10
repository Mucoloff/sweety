package dev.sweety.sql4j.impl.query.table;

import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class DropTable extends AbstractQuery<Void> {

    private final String sql;

    public DropTable(final String name) {
        this.sql = "DROP TABLE IF EXISTS " + name;
    }

    public DropTable(final Table<?> table) {
        this(table.name());
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
