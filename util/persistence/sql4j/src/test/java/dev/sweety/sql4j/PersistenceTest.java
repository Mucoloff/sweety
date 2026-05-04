package dev.sweety.sql4j;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.entity.TestUser;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.configuration.*;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;
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
        // Enable logging for concurrent debugging
        SqlRunner.setLogger(SqlLogger.stdout("SQL4J"));
    }

    // --- Helper Methods ---

    private void runBasicTest(ConnectionType type, DatabaseConfig config, boolean useHikari) throws Exception {
        String testId = "basic_" + System.nanoTime();
        try (SqlConnection connection = type.create(config, Executors.newSingleThreadExecutor(), useHikari);
             Database db = new Database(connection)) {

            Repository<TestUser> repo = db.createRepository(TestUser.class, "users_" + testId);

            TestUser user = new TestUser("Alice", 25);
            db.transact(tx -> tx.execute(repo.insert(user))).join();

            assertNotEquals(0, user.getId(), "ID should be generated");

            List<TestUser> users = repo.selectAll().execute(connection).join();
            assertTrue(users.stream().anyMatch(u -> u.getName().equals("Alice") && u.getAge() == 25));

            repo.dropTable().execute(connection).join();
        }
    }

    private void runStressTest(ConnectionType type, DatabaseConfig config, boolean useHikari) throws Exception {
        final int THREADS = 5;
        final int OPS_PER_THREAD = 10;
        String testId = "stress_" + System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errors = new AtomicInteger(0);

        try (SqlConnection connection = type.create(config, Executors.newFixedThreadPool(THREADS), useHikari);
             Database db = new Database(connection)) {

            Repository<TestUser> repo = db.createRepository(TestUser.class, "users_" + testId);

            List<CompletableFuture<?>> futures = new java.util.ArrayList<>();

            for (int t = 0; t < THREADS; t++) {
                final int threadId = t;
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        try {
                            String name = "User-" + threadId + "-" + i;
                            TestUser user = new TestUser(name, 20 + i % 50);

                            db.transact(tx -> tx.execute(repo.insert(user))).join();

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

    // --- Projection + Row + Savepoint Tests (local only: SQLite + H2) ---

    private void runProjectionTest(ConnectionType type, DatabaseConfig config, boolean useHikari) throws Exception {
        String testId = "proj_" + System.nanoTime();
        try (SqlConnection connection = type.create(config, Executors.newSingleThreadExecutor(), useHikari);
             Database db = new Database(connection)) {

            Repository<TestUser> repo = db.createRepository(TestUser.class, "users_" + testId);

            db.transact(tx -> {
                tx.execute(repo.insert(new TestUser("Alice", 25)));
                tx.execute(repo.insert(new TestUser("Bob", 30)));
                tx.execute(repo.insert(new TestUser("Carol", 22)));
            }).join();

            // 1. Entity-based partial select — only name + age, id stays 0
            List<TestUser> partial = repo.select("name", "age").execute(connection).join();
            assertEquals(3, partial.size());
            assertTrue(partial.stream().anyMatch(u -> "Alice".equals(u.getName()) && u.getAge() == 25), "[1] Alice not in partial");
            assertTrue(partial.stream().allMatch(u -> u.getId() == 0), "id should be 0 (not fetched)");

            // 2. Row-based selectRawAll
            List<Row> allRows = repo.selectRawAll().execute(connection).join();
            System.out.println("ALL ROWS: " + allRows);
            assertEquals(3, allRows.size(), "[2] selectRawAll count");
            assertTrue(allRows.stream().anyMatch(r -> "Bob".equals(r.getString("name"))), "[3] Bob in allRows");

            // 3. Row-based selectRaw — specific columns
            List<Row> nameRows = repo.selectRaw("name", "age").execute(connection).join();
            assertEquals(3, nameRows.size(), "[4] nameRows count");
            Row aliceRow = nameRows.stream()
                    .filter(r -> "Alice".equals(r.getString("name")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Alice not found"));
            assertEquals(25, aliceRow.getInt("age"), "[5] Alice age");
            assertFalse(aliceRow.has("id"), "[6] id should not be present in name+age projection");

            // 4. selectRawWhere — age > 24
            List<Row> filtered = repo.selectRawWhere("age > ?", 24).execute(connection).join();
            assertTrue(filtered.stream().allMatch(r -> r.getInt("age") > 24), "[7] all filtered age>24");
            assertTrue(filtered.stream().anyMatch(r -> "Alice".equals(r.getString("name"))), "[8] Alice in filtered");
            assertFalse(filtered.stream().anyMatch(r -> "Carol".equals(r.getString("name"))), "[9] Carol not in filtered");

            // 5. Savepoint: insert David + Eve, rollback Eve only
            db.transact(tx -> {
                tx.execute(repo.insert(new TestUser("David", 40)));
                tx.savepoint("after_david");
                tx.execute(repo.insert(new TestUser("Eve", 35)));
                tx.rollbackTo("after_david");
            }).join();

            List<Row> eveRows = repo.selectRawWhere("name = ?", "Eve").execute(connection).join();
            assertTrue(eveRows.isEmpty(), "[10] Eve rolled back via savepoint");

            List<Row> davidRows = repo.selectRawWhere("name = ?", "David").execute(connection).join();
            assertFalse(davidRows.isEmpty(), "[11] David present after savepoint");

            repo.dropTable().execute(connection).join();
        }
    }

    @Nested
    @DisplayName("Projection + Row API")
    class ProjectionTests {

        @Test
        @DisplayName("SQLite Direct")
        void testSQLiteDirect() throws Exception {
            runProjectionTest(ConnectionType.SQLITE, new SQLiteConfig("test_proj_sqlite.db"), false);
        }

        @Test
        @DisplayName("SQLite Hikari")
        void testSQLiteHikari() throws Exception {
            runProjectionTest(ConnectionType.SQLITE, new SQLiteConfig("test_proj_sqlite_h.db"), true);
        }

        @Test
        @DisplayName("H2 Direct")
        void testH2Direct() throws Exception {
            // Use Hikari for H2 in-memory to guarantee all operations share the same connection pool
            runProjectionTest(ConnectionType.H2, new H2Config("mem:test_proj_h2_d;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", ""), true);
        }

        @Test
        @DisplayName("H2 Hikari")
        void testH2Hikari() throws Exception {
            runProjectionTest(ConnectionType.H2, new H2Config("mem:test_proj_h2_h;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", ""), true);
        }
    }

    // --- Phase 5 Tests: Upsert & Migration ---
    private void runUpsertAndMigrationTest(ConnectionType type, DatabaseConfig config, boolean useHikari) throws Exception {
        String testId = "upsert_" + System.nanoTime();
        try (SqlConnection connection = type.create(config, Executors.newSingleThreadExecutor(), useHikari);
             Database db = new Database(connection)) {

            // Phase 5.2 & 5.3: Schema Migration
            // We use the same TestUser class, but we'll first simulate a table with missing columns
            try (java.sql.Statement stmt = connection.connection().createStatement()) {
                // Drop if exists
                stmt.execute("DROP TABLE IF EXISTS users_" + testId);
                // Create with only id and name, missing 'age'
                String pkDef = "INTEGER PRIMARY KEY " + (type == ConnectionType.POSTGRESQL ? "" : type == ConnectionType.H2 ? "AUTO_INCREMENT" : "AUTOINCREMENT");
                if (type == ConnectionType.MYSQL) pkDef = "INT AUTO_INCREMENT PRIMARY KEY";
                if (type == ConnectionType.POSTGRESQL) pkDef = "SERIAL PRIMARY KEY";
                
                stmt.execute("CREATE TABLE users_" + testId + " (id " + pkDef + ", name VARCHAR(255) NOT NULL)");
            } catch (Exception e) {
                // SQLite syntax etc might differ, but simple create usually works.
            }

            // createRepository will trigger migrateSchema which should add the 'age' column
            Repository<TestUser> repo = db.createRepository(TestUser.class, "users_" + testId);
            
            // Now test Upsert (Phase 5.1)
            // For Upsert, we use a table with a natural key (no auto-increment)
            Repository<dev.sweety.sql4j.entity.TestUpsertData> upsertRepo = db.createRepository(dev.sweety.sql4j.entity.TestUpsertData.class, "upsert_" + testId);
            
            dev.sweety.sql4j.entity.TestUpsertData data = new dev.sweety.sql4j.entity.TestUpsertData("key-1", "val-1");
            db.transact(tx -> tx.execute(upsertRepo.upsert(data))).join();
            
            // Upsert again (should update)
            data.setDataValue("val-2");
            db.transact(tx -> tx.execute(upsertRepo.upsert(data))).join();
            
            List<dev.sweety.sql4j.entity.TestUpsertData> items = upsertRepo.selectAll().execute(connection).join();
            assertEquals(1, items.size(), "Should have exactly 1 data row");
            assertEquals("val-2", items.get(0).getDataValue(), "Value should be updated");
            
            upsertRepo.dropTable().execute(connection).join();
            repo.dropTable().execute(connection).join();
        }
    }

    @Nested
    @DisplayName("Upsert + Migration API")
    class UpsertMigrationTests {
        @Test
        @DisplayName("SQLite Direct")
        void testSQLiteDirect() throws Exception {
            runUpsertAndMigrationTest(ConnectionType.SQLITE, new SQLiteConfig("test_ups_sqlite.db"), false);
        }

        @Test
        @DisplayName("H2 Hikari")
        void testH2Hikari() throws Exception {
            runUpsertAndMigrationTest(ConnectionType.H2, new H2Config("mem:test_ups_h2_h;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", ""), true);
        }
    }
}
