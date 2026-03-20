package dev.sweety.sql4j.impl.configuration;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;

public record PostgreSQLConfig(String host, int port, String database, String user, String password, String properties) implements DatabaseConfig {
    @Override
    public DialectType dialectType() {
        return DialectType.POSTGRESQL;
    }

    @Override
    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database + (properties != null && !properties.isEmpty() ? "?" + properties : "");
    }
}

