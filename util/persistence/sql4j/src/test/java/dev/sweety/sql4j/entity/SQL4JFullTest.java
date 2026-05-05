package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.connection.SqlRunner;
import dev.sweety.sql4j.api.util.SqlLogger;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.impl.connection.dialect.SqliteDialect;
import dev.sweety.sql4j.api.obj.Row;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SQL4JFullTest {

    private Database db;
    private SqlConnection con;
    private Repository<User> users;
    private Repository<Project> projects;
    private Repository<Task> tasks;

    @BeforeEach
    public void setup() throws Exception {
        SqlRunner.setLogger(SqlLogger.stdout());
        // Use a UNIQUE FILE-based database for EACH test to ensure total isolation and persistence
        String dbName = "sql4j_test_" + System.nanoTime() + ".db";
        java.io.File dbFile = new java.io.File(dbName);
        dbFile.deleteOnExit(); // Clean up on JVM exit

        con = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), dbName);

        db = new Database(con);

        users = db.createRepository(User.class);
        projects = db.createRepository(Project.class);
        tasks = db.createRepository(Task.class);

        db.migrateAll();
    }

    @Test
    @Order(1)
    @DisplayName("1. Basic CRUD & Reflection-Free Access")
    public void testBasicCrud() {
        User u = new User();
        u.setName("Alice");
        u.setAge(25);
        u.setRole("ADMIN");

        // Insert
        users.insert(u).execute(con).join();
        assertNotNull(u.getId());

        // Select by PK (Fluent API)
        User found = users.pk(u.getId()).find().execute(con).join();
        assertNotNull(found);
        assertEquals("Alice", found.getName());
        assertEquals(25, found.getAge());

        // Update via PK context
        found.setAge(26);
        users.pk(found.getId()).update(found).execute(con).join();

        User updated = users.pk(u.getId()).find().execute(con).join();
        assertEquals(26, updated.getAge());

        // Select All
        List<User> all = users.selectAll().execute(con).join();
        assertEquals(1, all.size());
    }

    @Test
    @Order(2)
    @DisplayName("2. Advanced Criterion DSL")
    public void testCriterionDsl() {
        // Insert data for this test
        User u1 = new User();
        u1.setName("Alice");
        u1.setAge(25);
        u1.setRole("ADMIN");
        User u2 = new User();
        u2.setName("Bob");
        u2.setAge(30);
        u2.setRole("USER");
        User u3 = new User();
        u3.setName("Charlie");
        u3.setAge(35);
        u3.setRole("USER");
        users.insert(u1).execute(con).join();
        users.insert(u2).execute(con).join();
        users.insert(u3).execute(con).join();

        // AND + GT
        List<User> result = users.selectAll()
                .where(UserTable.AGE.gt(28).and(UserTable.ROLE.eq("USER")))
                .execute(con).join();
        assertEquals(2, result.size());

        // OR
        result = users.selectAll()
                .where(UserTable.NAME.eq("Alice").or(UserTable.NAME.eq("Charlie")))
                .execute(con).join();
        assertEquals(2, result.size());

        // LIKE
        result = users.selectAll()
                .where(UserTable.NAME.like("%ar%"))
                .execute(con).join();
        assertEquals(1, result.size());
        assertEquals("Charlie", result.get(0).getName());

        // IN
        result = users.selectAll()
                .where(UserTable.NAME.in("Alice", "Bob"))
                .execute(con).join();
        assertEquals(2, result.size());
    }

    @Test
    @Order(3)
    @DisplayName("3. Bulk Operations & Soft Delete")
    public void testBulkAndSoftDelete() {
        // Insert data for this test
        User u1 = new User();
        u1.setName("Alice");
        u1.setAge(25);
        u1.setRole("ADMIN");
        User u2 = new User();
        u2.setName("Bob");
        u2.setAge(30);
        u2.setRole("USER");
        User u3 = new User();
        u3.setName("Charlie");
        u3.setAge(35);
        u3.setRole("USER");
        users.insert(u1).execute(con).join();
        users.insert(u2).execute(con).join();
        users.insert(u3).execute(con).join();

        // Bulk Update
        int affected = users.updateWhere()
                .set(UserTable.ROLE, "SUPER_USER")
                .where(UserTable.AGE.gt(30))
                .execute(con).join();
        assertEquals(1, affected); // Only Charlie is > 30

        // Soft Delete via PK context
        users.pk(u1.getId()).delete().execute(con).join(); // Delete Alice

        List<User> active = users.selectAll().execute(con).join();
        assertEquals(2, active.size());
        assertFalse(active.stream().anyMatch(u -> u.getName().equals("Alice")));

        // withDeleted
        List<User> all = users.selectAll().withDeleted().execute(con).join();
        assertEquals(3, all.size());
        assertTrue(all.stream().anyMatch(u -> u.getName().equals("Alice")));

        // Bulk Delete
        users.deleteWhere().where(UserTable.NAME.eq("Bob")).execute(con).join();
        assertEquals(1, users.selectAll().execute(con).join().size());
    }

    @Test
    @Order(4)
    @DisplayName("4. Relations & Typed Joins")
    public void testRelationsAndJoins() {
        // Setup data for this test
        User charlie = new User();
        charlie.setName("Charlie");
        charlie.setAge(35);
        charlie.setRole("USER");
        users.insert(charlie).execute(con).join();

        Project p1 = new Project();
        p1.setTitle("SQL4J Implementation");
        p1.setOwner(charlie);
        projects.insert(p1).execute(con).join();

        Task t1 = new Task();
        t1.setDescription("Fix NPE");
        t1.setStatus("DONE");
        t1.setProject(p1);
        Task t2 = new Task();
        t2.setDescription("Add Tests");
        t2.setStatus("TODO");
        t2.setProject(p1);
        tasks.insert(t1).execute(con).join();
        tasks.insert(t2).execute(con).join();

        // Typed Join with Relation constant (Suffix _REL)
        List<Row> rows = users.joinBuilder()
                .join(UserTable.PROJECTS_REL)
                .join(ProjectTable.TASKS_REL)
                .where(UserTable.NAME.eq("Charlie"))
                .build().execute(con).join();

        assertNotNull(rows);
        assertEquals(2, rows.size()); // Row 1: Charlie-P1-T1, Row 2: Charlie-P1-T2

        // Hierarchy Mapping
        List<User> hierarchy = users.joinBuilder()
                .join(UserTable.PROJECTS_REL)
                .join(ProjectTable.TASKS_REL)
                .where(UserTable.NAME.eq("Charlie"))
                .build()
                .mapToHierarchy(User.class)
                .execute(con).join();

        assertEquals(1, hierarchy.size());
        User root = hierarchy.get(0);
        assertEquals("Charlie", root.getName());
        assertEquals(1, root.getProjects().size());
        assertEquals(2, root.getProjects().get(0).getTasks().size());
    }

    @Test
    @Order(5)
    @DisplayName("5. Transactions")
    public void testTransactions() {
        // No setup needed, uses fresh DB
        db.transact(tx -> {
            User u = new User();
            u.setName("Ghost");
            u.setAge(0);
            u.setRole("GHOST");
            tx.execute(users.insert(u));
            throw new RuntimeException("Rollback test");
        }).handle((res, ex) -> {
            assertNotNull(ex, "Transaction should have failed with an exception");
            return null;
        }).join();

        List<User> ghosts = users.selectAll().where(UserTable.NAME.eq("Ghost")).execute(con).join();
        assertEquals(0, ghosts.size(), "Database should be empty after rollback, but found: " + ghosts);
    }

    @AfterEach
    public void tearDown() {
        db.close();
    }
}
