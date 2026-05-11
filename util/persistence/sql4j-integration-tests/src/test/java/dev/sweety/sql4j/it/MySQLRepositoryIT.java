package dev.sweety.sql4j.it;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests against a containerised MySQL 8 database.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MySQLRepositoryIT extends AbstractRepositoryIT {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0");

    @Override
    protected DatabaseConfig openConfig() {
        return DatabaseConfig.mysql(
                mysql.getHost(),
                mysql.getMappedPort(MySQLContainer.MYSQL_PORT),
                mysql.getDatabaseName(),
                mysql.getUsername(),
                mysql.getPassword());
    }
}
