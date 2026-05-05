package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.Criterion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class CriterionTest {

    @Table.Info(name = "criterion_users")
    public static class User {
        @dev.sweety.sql4j.api.obj.Column.Info(primaryKey = true, autoIncrement = true)
        private int id;
        @dev.sweety.sql4j.api.obj.Column.Info
        private String name;
        @dev.sweety.sql4j.api.obj.Column.Info
        private int age;

        public User() {}
        public User(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    private Database db;
    private SqlConnection connection;
    private Repository<User> users;
    private String dbFile;

    @BeforeEach
    void setup() {
        dbFile = "test_crit_" + System.nanoTime() + ".db";
        connection = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), dbFile);
        db = new Database(connection);
        users = db.createRepository(User.class);
        
        users.insert(new User("Alice", 25)).execute(connection).join();
        users.insert(new User("Bob", 30)).execute(connection).join();
        users.insert(new User("Charlie", 35)).execute(connection).join();
    }

    @AfterEach
    void tearDown() {
        db.close();
        new java.io.File(dbFile).delete();
    }

    @Test
    void testCriteria() {
        Table<User> table = db.tableRegistry().get(User.class);
        
        // age > 28
        List<User> results = users.selectAll()
                .where(Criterion.gt(table.column("age"), 28))
                .execute(connection).join();
        
        assertEquals(2, results.size());
        
        // age > 28 AND name = 'Bob'
        results = users.selectAll()
                .where(Criterion.and(
                        Criterion.gt(table.column("age"), 28),
                        Criterion.eq(table.column("name"), "Bob")
                ))
                .execute(connection).join();
        
        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).getName());
        
        // name LIKE 'A%' OR name LIKE 'C%'
        results = users.selectAll()
                .where(Criterion.or(
                        Criterion.like(table.column("name"), "A%"),
                        Criterion.like(table.column("name"), "C%")
                ))
                .orderBy("name", true)
                .execute(connection).join();
        
        assertEquals(2, results.size());
        assertEquals("Alice", results.get(0).getName());
        assertEquals("Charlie", results.get(1).getName());
    }
}
