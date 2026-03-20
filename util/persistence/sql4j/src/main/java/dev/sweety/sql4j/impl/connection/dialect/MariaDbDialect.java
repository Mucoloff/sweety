package dev.sweety.sql4j.impl.connection.dialect;

public final class MariaDbDialect extends MySqlDialect {

    @Override
    public String name() {
        return "mariadb";
    }
}
