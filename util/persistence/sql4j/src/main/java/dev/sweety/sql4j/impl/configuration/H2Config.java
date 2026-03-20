package dev.sweety.sql4j.impl.configuration;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;

public record H2Config(String path, String user, String password) implements DatabaseConfig {
    @Override
    public DialectType dialectType() {
        return DialectType.H2;
    }

    @Override
    public String jdbcUrl() {
        return "jdbc:h2:" + path;
    }
}

