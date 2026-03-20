package dev.sweety.sql4j.api.configuration;

import dev.sweety.sql4j.impl.connection.dialect.DialectType;

public interface DatabaseConfig {
    DialectType dialectType();
    String jdbcUrl();
    default String user() { return null; }
    default String password() { return null; }
}

