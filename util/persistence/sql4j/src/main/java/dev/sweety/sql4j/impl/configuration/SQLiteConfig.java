package dev.sweety.sql4j.impl.configuration;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;

public record SQLiteConfig(String path) implements DatabaseConfig {
    @Override
    public DialectType dialectType() {
        return DialectType.SQLITE;
    }

    @Override
    public String jdbcUrl() {
        return "jdbc:sqlite:" + (path.endsWith(".db") ? path : path + ".db");
    }
}
