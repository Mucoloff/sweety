package dev.sweety.sql4j.impl.configuration;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;

public record MariaDBConfig(String host, int port, String database, String user, String password, String properties) implements DatabaseConfig {
    @Override
    public DialectType dialectType() {
        return DialectType.MARIADB;
    }

    @Override
    public String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database + (properties != null && !properties.isEmpty() ? "?" + properties : "");
    }
}

