package dev.sweety.sql4j.it;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests against a containerised PostgreSQL 16 database.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PostgreSQLRepositoryIT extends AbstractRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    protected DatabaseConfig openConfig() {
        return DatabaseConfig.postgresql(
                pg.getHost(),
                pg.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                pg.getDatabaseName(),
                pg.getUsername(),
                pg.getPassword());
    }
}
