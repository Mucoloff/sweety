package dev.sweety.sql4j.impl.connection.dialect;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.impl.connection.ConnectionType;

public enum DialectType {
    SQLITE(new SqliteDialect()),
    H2(new H2Dialect()),
    MYSQL(new MySqlDialect()),
    MARIADB(new MariaDbDialect()),
    POSTGRESQL(new PostgreSQLDialect()),
    ;

    public static final DialectType[] VALUES = values();

    private final Dialect dialect;

    DialectType(final Dialect dialect) {
        this.dialect = dialect;
    }

    public Dialect dialect() {
        return dialect;
    }

    public ConnectionType connectionType() {
        return ConnectionType.fromDialectType(this);
    }
}