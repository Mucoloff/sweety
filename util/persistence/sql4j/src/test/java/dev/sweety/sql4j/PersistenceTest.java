package dev.sweety.sql4j;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.QueryExecutor;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.configuration.*;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for SQL4J.
 * Tests every ConnectionType with both Direct (DriverManager) and Pooled (HikariCP) connections,
 * including basic functional tests and high-concurrency stress tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PersistenceTest {

    static {
        // Enhanced logging for concurrent debugging
        QueryExecutor.setLogger(SqlLogger.stdout("SQL4J][Thread-" + Thread.currentThread().threadId()));
    }

    // --- Helper Methods ---

    private void runBasicTest(ConnectionType type, DatabaseConfig config, boolean useHikari) throws Exception {
        String testId = "basic_" + System.nanoTime();
        try (SqlConnection connection = type.create(config, Executors.newSingleThreadExecutor(), useHikari);
             Database db = new Database(connection)) {

            Repository<TestUser> repo = db.createRepository(TestUser.class, "users_" + testId);
            repo.create(db.dialect(), true).execute(connection).join();

            TestUser user = new TestUser("Alice", 25);
            db.transaction(tx -> tx.execute(repo.insert(user))).join();

            assertNotEquals(0, user.getId(), "ID should be generated");

            List<TestUser> users = repo.selectAll().execute(connection).join();
            assertTrue(users.stream().anyMatch(u -> u.getName().equals("Alice") && u.getAge() == 25));

            repo.dropTable().execute(connection).join();
        }
    }

    private void runStressTest(ConnectionType type, DatabaseConfig config, boolean useHikari) throws Exception {
        final int THREADS = 5;
        final int OPS_PER_THREAD = 10; // Balanced for remote DB stability
        String testId = "stress_" + System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errors = new AtomicInteger(0);

        try (SqlConnection connection = type.create(config, Executors.newFixedThreadPool(THREADS), useHikari);
             Database db = new Database(connection)) {

            Repository<TestUser> repo = db.createRepository(TestUser.class, "users_" + testId);
            repo.create(db.dialect(), true).execute(connection).join();

            List<CompletableFuture<?>> futures = new java.util.ArrayList<>();

            for (int t = 0; t < THREADS; t++) {
                final int threadId = t;
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        try {
                            String name = "User-" + threadId + "-" + i;
                            TestUser user = new TestUser(name, 20 + i % 50);

                            db.transaction(tx -> tx.execute(repo.insert(user))).join();

                            List<TestUser> found = repo.selectAll().execute(connection).join();
                            if (found.stream().noneMatch(u -> u.getName().equals(name))) {
                                errors.incrementAndGet();
                            }
                        } catch (Exception e) {
                            System.err.println("Stress error: " + e.getMessage());
                            errors.incrementAndGet();
                        }
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            assertEquals(0, errors.get(), "Stress test failed with " + errors.get() + " inconsistencies/errors");

            repo.dropTable().execute(connection).join();
        } finally {
            executor.shutdown();
        }
    }

    private void createDatabase(DatabaseConfig config, String dbName) throws Exception {
        String baseUrl = config.jdbcUrl().replace("/" + dbName, "/");
        try (java.sql.Connection con = java.sql.DriverManager.getConnection(baseUrl, config.user(), config.password());
             java.sql.Statement stmt = con.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
        } catch (java.sql.SQLException e) {
            System.err.println("DB creation skip (might be restricted): " + e.getMessage());
        }
    }

    // --- SQLite Tests ---

    @Nested
    @DisplayName("SQLite Persistence")
    class SQLiteTests {
        private final SQLiteConfig config = new SQLiteConfig("test_sqlite.db");

        @Test
        @DisplayName("Basic Direct")
        void testDirect() throws Exception {
            runBasicTest(ConnectionType.SQLITE, config, false);
        }

        @Test
        @DisplayName("Basic Hikari")
        void testHikari() throws Exception {
            runBasicTest(ConnectionType.SQLITE, config, true);
        }

        @Test
        @DisplayName("Stress Direct")
        void testStressDirect() throws Exception {
            runStressTest(ConnectionType.SQLITE, config, false);
        }

        @Test
        @DisplayName("Stress Hikari")
        void testStressHikari() throws Exception {
            runStressTest(ConnectionType.SQLITE, config, true);
        }
    }

    // --- H2 Tests ---

    @Nested
    @DisplayName("H2 Persistence")
    class H2Tests {
        private final H2Config config = new H2Config("mem:test_h2;DB_CLOSE_DELAY=-1", "sa", "");

        @Test
        @DisplayName("Basic Direct")
        void testDirect() throws Exception {
            runBasicTest(ConnectionType.H2, config, false);
        }

        @Test
        @DisplayName("Basic Hikari")
        void testHikari() throws Exception {
            runBasicTest(ConnectionType.H2, config, true);
        }

        @Test
        @DisplayName("Stress Direct")
        void testStressDirect() throws Exception {
            runStressTest(ConnectionType.H2, config, false);
        }

        @Test
        @DisplayName("Stress Hikari")
        void testStressHikari() throws Exception {
            runStressTest(ConnectionType.H2, config, true);
        }
    }

    // --- MySQL Tests (Remote Aiven) ---

    @Nested
    @DisplayName("MySQL Persistence")
    class MySQLTests {
        private final MySQLConfig config = new MySQLConfig("mysql-326eb984-mysqltest-1234.j.aivencloud.com", 18035, "defaultdb", "avnadmin", "AVNS_dPYSQVKBP1Rqnn8mEJO", "ssl-mode=REQUIRED");

        @Test
        @DisplayName("Basic Direct")
        void testDirect() throws Exception {
            runBasicTest(ConnectionType.MYSQL, config, false);
        }

        @Test
        @DisplayName("Basic Hikari")
        void testHikari() throws Exception {
            runBasicTest(ConnectionType.MYSQL, config, true);
        }

        @Test
        @DisplayName("Stress Direct")
        void testStressDirect() throws Exception {
            runStressTest(ConnectionType.MYSQL, config, false);
        }

        @Test
        @DisplayName("Stress Hikari")
        void testStressHikari() throws Exception {
            runStressTest(ConnectionType.MYSQL, config, true);
        }
    }

    // --- MariaDB Tests (Remote SkySQL) ---

    @Nested
    @DisplayName("MariaDB Persistence")
    class MariaDBTests {
        private final String dbName = "sql4j_test_" + (System.currentTimeMillis() % 1000);
        private final MariaDBConfig config = new MariaDBConfig("serverless-europe-west4.sysp0000.db2.skysql.com", 4060, dbName, "dbpgf07956063", "zHGT19mXk|3NAFoQGu1Luf1", "sslMode=verify-full");

        @BeforeEach
        void setup() throws Exception {
            createDatabase(config, dbName);
        }

        @Test
        @DisplayName("Basic Direct")
        void testDirect() throws Exception {
            runBasicTest(ConnectionType.MARIADB, config, false);
        }

        @Test
        @DisplayName("Basic Hikari")
        void testHikari() throws Exception {
            runBasicTest(ConnectionType.MARIADB, config, true);
        }

        @Test
        @DisplayName("Stress Direct")
        void testStressDirect() throws Exception {
            runStressTest(ConnectionType.MARIADB, config, false);
        }

        @Test
        @DisplayName("Stress Hikari")
        void testStressHikari() throws Exception {
            runStressTest(ConnectionType.MARIADB, config, true);
        }
    }

    // --- PostgreSQL Tests (Remote Supabase) ---

    @Nested
    @DisplayName("PostgreSQL Persistence")
    class PostgreSQLTests {
        private final PostgreSQLConfig config = new PostgreSQLConfig("db.qllrmbtyfeispovcdzny.supabase.co", 5432, "postgres", "postgres", "kergeC-soxty9-qozkym", null);

        @Test
        @DisplayName("Basic Direct")
        void testDirect() throws Exception {
            runBasicTest(ConnectionType.POSTGRESQL, config, false);
        }

        @Test
        @DisplayName("Basic Hikari")
        void testHikari() throws Exception {
            runBasicTest(ConnectionType.POSTGRESQL, config, true);
        }

        @Test
        @DisplayName("Stress Direct")
        void testStressDirect() throws Exception {
            runStressTest(ConnectionType.POSTGRESQL, config, false);
        }

        @Test
        @DisplayName("Stress Hikari")
        void testStressHikari() throws Exception {
            runStressTest(ConnectionType.POSTGRESQL, config, true);
        }
    }
}
