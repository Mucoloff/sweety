package dev.sweety.sql4j.it;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import org.junit.jupiter.api.Tag;

/**
 * Integration tests against an in-memory H2 database.
 *
 * <p>No container required — safe to run in every CI environment.
 */
@Tag("integration")
class H2RepositoryIT extends AbstractRepositoryIT {

    @Override
    protected DatabaseConfig openConfig() {
        return DatabaseConfig.h2("mem:it_h2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }
}
