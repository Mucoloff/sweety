package dev.sweety.sql4j.impl.connection.mysql.maria;

import dev.sweety.sql4j.impl.connection.mysql.MySqlDialect;

public final class MariaDbDialect extends MySqlDialect {

    @Override
    public String name() {
        return "mariadb";
    }
}
