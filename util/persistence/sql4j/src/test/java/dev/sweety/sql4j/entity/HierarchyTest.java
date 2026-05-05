package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class HierarchyTest {

    private Database db;
    private SqlConnection connection;
    private Repository<UserRel> users;
    private Repository<OrderRel> orders;
    private String dbFile;

    @BeforeEach
    void setup() {
        dbFile = "test_hier_" + System.nanoTime() + ".db";
        connection = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), dbFile);
        dev.sweety.sql4j.api.connection.SqlRunner.setLogger(dev.sweety.sql4j.api.util.SqlLogger.nop());
        db = new Database(connection);
        users = db.createRepository(UserRel.class);
        orders = db.createRepository(OrderRel.class);
    }

    @AfterEach
    void tearDown() {
        db.close();
        new java.io.File(dbFile).delete();
    }

    @Test
    void testMapToHierarchyOneToMany() {
        // Seed
        UserRel alice = users.insert(new UserRel("Alice")).execute(connection).join().entity();
        orders.insert(new OrderRel("Laptop", alice)).execute(connection).join();
        orders.insert(new OrderRel("Mouse", alice)).execute(connection).join();

        UserRel bob = users.insert(new UserRel("Bob")).execute(connection).join().entity();
        orders.insert(new OrderRel("Phone", bob)).execute(connection).join();

        // Query with JOIN
        List<UserRel> results = users.joinBuilder()
                .join(orders.table())
                .on(users.table().column("id"), orders.table().column("user_id"))
                .build()
                .mapToHierarchy(UserRel.class)
                .execute(connection).join();

        assertEquals(2, results.size());
        
        UserRel rAlice = results.stream().filter(u -> u.getName().equals("Alice")).findFirst().orElseThrow();
        assertNotNull(rAlice.getOrders());
        assertEquals(2, rAlice.getOrders().size());
        assertTrue(rAlice.getOrders().stream().anyMatch(o -> o.getProduct().equals("Laptop")));
        assertTrue(rAlice.getOrders().stream().anyMatch(o -> o.getProduct().equals("Mouse")));

        UserRel rBob = results.stream().filter(u -> u.getName().equals("Bob")).findFirst().orElseThrow();
        assertEquals(1, rBob.getOrders().size());
        assertEquals("Phone", rBob.getOrders().get(0).getProduct());
    }
}
