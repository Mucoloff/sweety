package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.query.Aggregate;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SQL4JPhase5Test {

    private Database db;
    private SqlConnection con;
    private Repository<User> users;

    @BeforeAll
    void setup() throws Exception {
        new File("test_phase5.db").delete();
        con = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), "test_phase5.db");
        db = new Database(con);
        users = db.createRepository(User.class);
        db.migrateAll();

        // Data setup
        users.insert(new User() {{ setName("A1"); setAge(20); setRole(Role.ADMIN); }}).execute(con).join();
        users.insert(new User() {{ setName("A2"); setAge(30); setRole(Role.ADMIN); }}).execute(con).join();
        users.insert(new User() {{ setName("U1"); setAge(25); setRole(Role.USER);  }}).execute(con).join();
    }

    @AfterAll
    void cleanup() throws Exception {
        db.close();
        new File("test_phase5.db").delete();
    }

    @Test
    @DisplayName("Aggregation: Count and GroupBy")
    void testCountGroupBy() {
        // SELECT role, COUNT(id) FROM users GROUP BY role
        List<Row> results = users.select()
                .select(UserTable.ROLE, Aggregate.count(UserTable.ID))
                .groupBy(UserTable.ROLE)
                .executeAggregate(con).join();

        assertEquals(2, results.size());
        Row adminRow = results.stream().filter(r -> Role.ADMIN.name().equals(r.get("role"))).findFirst().orElseThrow();
        assertEquals(2, adminRow.getLong("count_id"));

        Row userRow = results.stream().filter(r -> Role.USER.name().equals(r.get("role"))).findFirst().orElseThrow();
        assertEquals(1, userRow.getLong("count_id"));
    }

    @Test
    @DisplayName("Aggregation: Avg and Having")
    void testAvgHaving() {
        // SELECT role, AVG(age) FROM users GROUP BY role HAVING AVG(age) > 22
        List<Row> results = users.select()
                .select(UserTable.ROLE, Aggregate.avg(UserTable.AGE))
                .groupBy(UserTable.ROLE)
                .having(Criterion.gt(Aggregate.avg(UserTable.AGE), 22))
                .executeAggregate(con).join();

        // Both roles have avg age > 22 (ADMIN: 25, USER: 25)
        assertEquals(2, results.size());
    }
}
