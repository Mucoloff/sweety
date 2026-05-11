package dev.sweety.sql4j.it;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests against a containerised MariaDB 11 database.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MariaDBRepositoryIT extends AbstractRepositoryIT {

    @Container
    static final MariaDBContainer<?> mariadb =
            new MariaDBContainer<>("mariadb:11");

    @Override
    protected DatabaseConfig openConfig() {
        return DatabaseConfig.mariadb(
                mariadb.getHost(),
                mariadb.getMappedPort(3306),
                mariadb.getDatabaseName(),
                mariadb.getUsername(),
                mariadb.getPassword());
    }
}
