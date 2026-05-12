package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.query.Page;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.api.repository.Repository;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SQL4JPhase3Test {

    private static Database db;
    private static SqlConnection con;
    private static Repository<User> users;

    @BeforeAll
    static void setup() throws Exception {
        SqlRunner.setLogger(SqlLogger.stdout());
        Files.deleteIfExists(Path.of("test_phase3.db"));
        String dbName = "test_phase3.db";
        con = dev.sweety.sql4j.impl.connection.ConnectionType.SQLITE.create(java.util.concurrent.Executors.newSingleThreadExecutor(), dbName);
        db = new Database(con);
        users = db.createRepository(User.class);
        db.migrateAll();
    }

    @AfterAll
    static void cleanup() throws Exception {
        db.close();
        Files.deleteIfExists(Path.of("test_phase3.db"));
    }

    @Test
    @Order(1)
    void testBatchInsert() {
        List<User> batch = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User u = new User();
            u.setName("User" + i);
            u.setAge(20 + i);
            u.setRole(Role.USER);
            batch.add(u);
        }

        long start = System.currentTimeMillis();
        int[] results = users.insertBatch(batch).execute(con).join();
        long end = System.currentTimeMillis();

        assertEquals(100, results.length);
        for (int r : results) {
            assertTrue(r > 0 || r == -2); // -2 is SUCCESS_NO_INFO in some drivers
        }
        System.out.println("Batch insert of 100 records took: " + (end - start) + "ms");

        long count = users.select().execute(con).join().size();
        assertEquals(100, count);
    }

    @Test
    @Order(2)
    void testPagination() {
        // Page 0, Size 10
        Page<User> page0 = users.select().orderBy("name", true).executePage(con, 0, 10).join();
        assertEquals(10, page0.content().size());
        assertEquals(100, page0.totalElements());
        assertEquals(10, page0.totalPages());
        assertEquals(0, page0.currentPage());
        assertTrue(page0.hasNext());
        assertFalse(page0.hasPrevious());
        assertEquals("User0", page0.content().get(0).getName());

        // Page 9, Size 10
        Page<User> page9 = users.select().orderBy("name", true).executePage(con, 9, 10).join();
        assertEquals(10, page9.content().size());
        assertEquals(9, page9.currentPage());
        assertFalse(page9.hasNext());
        assertTrue(page9.hasPrevious());
    }

    @Test
    @Order(3)
    void testBatchUpdate() {
        List<User> all = users.select().execute(con).join();
        for (User u : all) {
            u.setAge(u.getAge() + 10);
        }

        int[] results = users.updateBatch(all).execute(con).join();
        assertEquals(100, results.length);

        User first = users.select().where(UserTable.NAME.eq("User0")).execute(con).join().get(0);
        assertEquals(30, first.getAge());
    }
}
