package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.interceptor.QueryInterceptor;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SQL4JPhase4Test {

    private Database db;
    private SqlConnection con;
    private Repository<User> users;

    @BeforeAll
    void setup() {
        new File("test_phase4.db").delete();
        con = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), "test_phase4.db");
        db = new Database(con);
        users = db.createRepository(User.class);
        db.migrateAll();

        // Insert some data
        List<User> batch = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User u = new User();
            u.setName("StreamUser" + i);
            u.setAge(20 + i);
            u.setRole(Role.USER);
            batch.add(u);
        }
        users.insertBatch(batch).execute(con).join();
    }

    @AfterAll
    void cleanup() {
        db.close();
        new File("test_phase4.db").delete();
    }

    @Test
    @DisplayName("Interceptor Verification")
    void testInterceptors() {
        AtomicInteger preCount = new AtomicInteger();
        AtomicInteger postCount = new AtomicInteger();

        db.addInterceptor(new QueryInterceptor() {
            @Override
            public void preExecute(Query<?> query, Connection connection) {
                preCount.incrementAndGet();
            }

            @Override
            public void postExecute(Query<?> query, Object result, long durationNs) {
                postCount.incrementAndGet();
            }
        });

        users.select().execute(con).join();

        assertEquals(1, preCount.get(), "preExecute should be called once");
        assertEquals(1, postCount.get(), "postExecute should be called once");
    }

    @Test
    @DisplayName("Result Streaming Verification")
    void testStreaming() {
        try (Stream<User> stream = users.select().executeStream(con).join()) {
            long count = stream.peek(u -> assertNotNull(u.getName())).count();
            assertEquals(50, count, "Should stream all 50 users");
        }
    }

    @Test
    @DisplayName("Slow Query Logging Verification")
    void testSlowQueryLogging() {
        List<String> logs = new ArrayList<>();
        SqlRunner.setLogger(logs::add);

        SqlRunner.setSlowQueryThresholdMs(0); // Everything is slow

        users.select().execute(con).join();

        assertTrue(logs.stream().anyMatch(l -> l.contains("[WARNING] SLOW QUERY DETECTED")), "Should log slow query warning");
        
        SqlRunner.setSlowQueryThresholdMs(500); // Reset
    }
}
