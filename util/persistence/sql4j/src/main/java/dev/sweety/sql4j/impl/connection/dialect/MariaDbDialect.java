package dev.sweety.sql4j.impl.connection.dialect;

final class MariaDbDialect extends MySqlDialect {

    @Override
    public String name() {
        return "mariadb";
    }
}
