package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.configuration.SQL4JConfig;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SQL4JConfig}.
 * No database connection required.
 */
class SQL4JConfigTest {

    private static SQL4JConfig config(String password) {
        return new SQL4JConfig(
                DialectType.MYSQL,
                "jdbc:mysql://localhost:3306/mydb",
                "admin",
                password,
                true,
                SQL4JConfig.HikariTuning.defaults(),
                true,
                null
        );
    }

    @Test
    void toString_masksPassword() {
        SQL4JConfig cfg = config("super-secret");
        String repr = cfg.toString();
        assertFalse(repr.contains("super-secret"), "Password must not appear in toString()");
        assertTrue(repr.contains("password=****"), "Masked placeholder must appear");
    }

    @Test
    void toString_nullPassword_showsNull() {
        SQL4JConfig cfg = config(null);
        String repr = cfg.toString();
        assertTrue(repr.contains("password=null"), "Null password should be shown as null, not masked");
    }

    @Test
    void hikariTuningDefaults() {
        SQL4JConfig.HikariTuning tuning = SQL4JConfig.HikariTuning.defaults();
        assertEquals(10, tuning.maxPoolSize());
        assertEquals(10, tuning.minIdle());
        assertNotNull(tuning.connectionTimeout());
        assertNotNull(tuning.idleTimeout());
        assertNotNull(tuning.maxLifetime());
    }

    @Test
    void configCarriesCorrectDialect() {
        SQL4JConfig cfg = config("pass");
        assertEquals(DialectType.MYSQL, cfg.dialect());
    }

    @Test
    void configCacheEnabled_default() {
        SQL4JConfig cfg = config("pass");
        assertTrue(cfg.cacheEnabled());
    }

    @Test
    void configUseHikari_default() {
        SQL4JConfig cfg = config("pass");
        assertTrue(cfg.useHikari());
    }
}
