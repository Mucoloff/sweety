package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.impl.connection.mysql.MySqlDialect;
import dev.sweety.sql4j.impl.connection.mysql.maria.MariaDbDialect;
import dev.sweety.sql4j.impl.connection.sqlite.SqliteDialect;
import lombok.Getter;

@Getter
public enum DialectType {
    SQLITE(new SqliteDialect()),
    //H2(),
    MYSQL(new MySqlDialect()),
    MARIADB(new MariaDbDialect()),
    //POSTGRESQL(),
    ;

    public static final DialectType[] VALUES = values();

    private final Dialect dialect;

    DialectType(final Dialect dialect) {
        this.dialect = dialect;
    }
}