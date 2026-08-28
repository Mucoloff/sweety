package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.query.Aggregate;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SQL4JPhase8Test {

    private Database db;
    private SqlConnection con;
    private Repository<User> users;

    @BeforeAll
    void setup() throws Exception {
        Files.deleteIfExists(Path.of("test_phase8.db"));
        con = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), "test_phase8.db");
        db = new Database(con);
        users = db.createRepository(User.class);
        db.migrateAll();
        
        // Seed data
        seedUser("Alice", 25, Role.ADMIN);
        seedUser("Bob", 30, Role.USER);
        seedUser("Charlie", 25, Role.USER);
        seedUser("David", 35, Role.USER);
    }

    private void seedUser(String name, int age, Role role) {
        User u = new User();
        u.setName(name);
        u.setAge(age);
        u.setRole(role);
        users.insert(u).execute(con).join();
    }

    @AfterAll
    void cleanup() throws Exception {
        db.close();
        Files.deleteIfExists(Path.of("test_phase8.db"));
    }

    @AfterEach
    void resetLogger() {
        SqlRunner.setLogger(SqlLogger.nop());
    }

    @Test
    @DisplayName("L2 Cache: Read Hit (Fetch by ID)")
    void testCacheReadHit() {
        // Find Alice to get her PK
        User alice = users.select().where(UserTable.NAME.eq("Alice")).execute(con).join().getFirst();
        Object pk = alice.getId();

        // Setup logger to count queries
        AtomicInteger queryCount = new AtomicInteger();
        SqlRunner.setLogger(message -> {
            if (message.contains("Executing SQL")) {
                queryCount.incrementAndGet();
            }
        });

        // Find by PK - should be a cache hit (0 queries)
        User found = users.pk(pk).find().execute(con).join();
        assertNotNull(found);
        assertEquals(0, queryCount.get(), "Should be a cache hit because alice was just selected/inserted");

        // Clear cache and find again
        db.entityCache().clear();
        User found2 = users.pk(pk).find().execute(con).join();
        assertNotNull(found2);
        assertTrue(queryCount.get() > 0, "Should hit DB after cache clear");

        SqlRunner.setLogger(SqlLogger.nop());
    }

    @Test
    @DisplayName("Aggregation: GROUP BY and HAVING")
    void testAggregation() {
        // Group by age, count users
        List<dev.sweety.sql4j.api.obj.Row> results = users.select(UserTable.AGE, Aggregate.count(UserTable.ID))
                .groupBy(UserTable.AGE)
                .orderBy("age", true)
                .executeAggregate(con)
                .join();

        assertEquals(3, results.size()); // Ages: 25, 30, 35
        
        // Age 25 has 2 users (Alice and Charlie)
        assertEquals(25, results.getFirst().getInt("age"));
        assertEquals(2L, ((Number)results.getFirst().get("count_id")).longValue());

        // Having count > 1
        List<dev.sweety.sql4j.api.obj.Row> filtered = users.select(UserTable.AGE, Aggregate.count(UserTable.ID))
                .groupBy(UserTable.AGE)
                .having(Aggregate.count(UserTable.ID).gt(1))
                .executeAggregate(con)
                .join();

        assertEquals(1, filtered.size());
        assertEquals(25, filtered.getFirst().getInt("age"));
    }

    @Test
    @DisplayName("Hardening: Immutability of Where Queries")
    void testImmutability() {
        dev.sweety.sql4j.api.query.ConditionalDeleteQuery<User> q1 = users.deleteWhere().where(UserTable.NAME.eq("Alice"));
        dev.sweety.sql4j.api.query.ConditionalDeleteQuery<User> q2 = q1.hardDelete();
        
        assertNotSame(q1, q2, "hardDelete() should return a NEW instance in the immutable pattern");
        
        // Ensure q1 is still soft delete (default) and q2 is hard delete
        assertTrue(q2.sql().contains("DELETE FROM"), "q2 should be a hard delete");
        assertTrue(q1.sql().contains("UPDATE"), "q1 should remain a soft delete (UPDATE)");
    }
}
