package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SQL4JPhase6Test {

    private Database db;
    private SqlConnection con;
    private Repository<User> users;

    @BeforeAll
    void setup() throws Exception {
        new File("test_phase6.db").delete();
        con = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), "test_phase6.db");
        db = new Database(con);
        users = db.createRepository(User.class);
        db.migrateAll();
    }

    @AfterAll
    void cleanup() throws Exception {
        db.close();
        new File("test_phase6.db").delete();
    }

    @Test
    @DisplayName("L2 Cache: Read Hit")
    void testCacheReadHit() {
        User u = new User();
        u.setName("CacheUser");
        u.setAge(30);
        u.setRole("USER");
        
        // 1. Insert (fills cache)
        users.insert(u).execute(con).join();
        Object pk = u.getId();
        assertNotNull(pk);

        // 2. Setup logger to count queries
        AtomicInteger queryCount = new AtomicInteger();
        SqlRunner.setLogger(message -> {
            if (message.contains("Executing SQL")) queryCount.incrementAndGet();
        });

        // 3. Find by PK (should be cache hit)
        User found1 = users.pk(pk).find().execute(con).join();
        assertNotNull(found1);
        assertEquals(0, queryCount.get(), "First find should be a cache hit (0 queries)");

        // 4. Clear cache and find again
        db.entityCache().clear();
        User found2 = users.pk(pk).find().execute(con).join();
        assertNotNull(found2);
        assertEquals(1, queryCount.get(), "Find after clear should hit DB (1 query)");
        
        SqlRunner.setLogger(SqlLogger.nop());
    }

    @Test
    @DisplayName("L2 Cache: Invalidation on Update")
    void testCacheInvalidationUpdate() {
        User u = new User();
        u.setName("UpdateUser");
        u.setRole("USER");
        users.insert(u).execute(con).join();
        Object pk = u.getId();

        // Update name
        u.setName("UpdatedName");
        users.update(u).execute(con).join();

        // Check if cache has updated name
        User cached = db.entityCache().get(User.class, pk);
        assertEquals("UpdatedName", cached.getName());
    }

    @Test
    @DisplayName("L2 Cache: Invalidation on Delete")
    void testCacheInvalidationDelete() {
        User u = new User();
        u.setName("DeleteUser");
        u.setRole("USER");
        users.insert(u).execute(con).join();
        Object pk = u.getId();

        assertNotNull(db.entityCache().get(User.class, pk));

        // Delete
        users.delete(u).execute(con).join();

        assertNull(db.entityCache().get(User.class, pk), "Cache should be evicted after delete");
    }
}
