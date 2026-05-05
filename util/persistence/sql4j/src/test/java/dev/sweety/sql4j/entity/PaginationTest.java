package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.api.obj.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class PaginationTest {

    private Database db;
    private SqlConnection connection;
    private Repository<TestUser> users;
    private String dbFile;

    @BeforeEach
    void setup() {
        dbFile = "test_pag_" + System.nanoTime() + ".db";
        connection = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), dbFile);
        dev.sweety.sql4j.api.connection.SqlRunner.setLogger(dev.sweety.sql4j.api.util.SqlLogger.nop()); // Disable log for performance
        db = new Database(connection);
        users = db.createRepository(TestUser.class);
        
        // Seed data
        for (int i = 1; i <= 10; i++) {
            users.insert(new TestUser("User" + i, 20 + i)).execute(connection).join();
        }
    }

    @AfterEach
    void tearDown() {
        db.close();
        new java.io.File(dbFile).delete();
    }

    @Test
    void testLimitAndOffset() {
        List<TestUser> page1 = users.selectAll()
                .orderBy("id", true)
                .limit(3)
                .execute(connection).join();
        
        assertEquals(3, page1.size());
        page1.forEach(u -> System.out.println("DEBUG: ID=" + u.getId() + ", Name=" + u.getName()));
        assertEquals("User1", page1.get(0).getName());
        assertEquals("User3", page1.get(2).getName());

        List<TestUser> page2 = users.selectAll()
                .orderBy("id", true)
                .limit(3)
                .offset(3)
                .execute(connection).join();

        assertEquals(3, page2.size());
        assertEquals("User4", page2.get(0).getName());
        assertEquals("User6", page2.get(2).getName());
    }

    @Test
    void testOrderBy() {
        List<TestUser> desc = users.selectAll()
                .orderBy("age", false)
                .limit(1)
                .execute(connection).join();
        
        assertEquals(1, desc.size());
        assertEquals(30, desc.get(0).getAge()); // User10 is 20+10=30
    }

    @Test
    void testRawPagination() {
        List<Row> rows = users.selectRawAll()
                .orderBy("name", true)
                .limit(2)
                .offset(1)
                .execute(connection).join();
        
        assertEquals(2, rows.size());
        // Lexicographical order: User1, User10, User2, ...
        // Page 1 (limit 2): User1, User10
        // Page 1 offset 1: User10, User2
        assertEquals("User10", rows.get(0).getString("name"));
        assertEquals("User2", rows.get(1).getString("name"));
    }
}
