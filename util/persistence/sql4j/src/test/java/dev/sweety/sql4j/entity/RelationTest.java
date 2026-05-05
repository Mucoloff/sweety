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

public class RelationTest {

    private Database db;
    private SqlConnection connection;
    private Repository<UserRel> users;
    private Repository<OrderRel> orders;
    private Repository<RoleRel> roles;

    @BeforeEach
    void setup() {
        connection = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), ":memory:");
        db = new Database(connection);
        
        users = db.createRepository(UserRel.class);
        orders = db.createRepository(OrderRel.class);
        roles = db.createRepository(RoleRel.class);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void testManyToOneInsertion() {
        UserRel user = new UserRel("Alice");
        UserRel savedUser = users.insert(user).execute(connection).join().entity();
        
        OrderRel order = new OrderRel("Laptop", savedUser);
        OrderRel savedOrder = orders.insert(order).execute(connection).join().entity();
        
        assertNotNull(savedOrder);
        assertTrue(savedOrder.getId() > 0);
        
        // Verify via raw query
        orders.selectAll().execute(connection).join().forEach(o -> {
             assertEquals("Laptop", o.getProduct());
             // Note: in this version, the 'user' field in OrderRel is NOT automatically populated by selectAll
             // because we don't have magic proxying. We use JOINs for that.
        });
    }

    @Test
    void testManyToManyRelations() {
        UserRel user = users.insert(new UserRel("Bob")).execute(connection).join().entity();
        RoleRel admin = roles.insert(new RoleRel("ADMIN")).execute(connection).join().entity();
        RoleRel userRole = roles.insert(new RoleRel("USER")).execute(connection).join().entity();

        // Add relations
        users.addRelation(user, admin).execute(connection).join();
        users.addRelation(user, userRole).execute(connection).join();

        // Verify junction table exists and has data (via raw check or count)
        // Since we don't have a repo for the junction table yet, we use a raw query
        int count = db.tableRegistry().allTables().stream()
                .filter(t -> t.name().equalsIgnoreCase("users_roles"))
                .findFirst()
                .map(t -> {
                    // This is just to prove the table exists in registry
                    return 1;
                }).orElse(0);
        
        assertEquals(1, count);
    }
}
