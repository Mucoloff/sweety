package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.Default;
import dev.sweety.sql4j.api.obj.annotation.SoftDelete;
import dev.sweety.sql4j.api.obj.annotation.Unique;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class EnterpriseTest {

    @Table.Info(name = "enterprise_users")
    public static class EnterpriseUser {
        @Column.Info(primaryKey = true, autoIncrement = true)
        private int id;

        @Unique
        @Column.Info
        private String email;

        @Default("'N/A'")
        @Column.Info
        private String bio;

        @SoftDelete
        @Column.Info
        private boolean deleted;

        public EnterpriseUser() {}
        public EnterpriseUser(String email) { this.email = email; }
        
        public int getId() { return id; }
        public String getEmail() { return email; }
        public String getBio() { return bio; }
        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
    }

    private Database db;
    private SqlConnection connection;
    private Repository<EnterpriseUser> users;
    private String dbFile;

    @BeforeEach
    void setup() {
        dbFile = "test_ent_" + System.nanoTime() + ".db";
        connection = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), dbFile);
        dev.sweety.sql4j.api.connection.SqlRunner.setLogger(dev.sweety.sql4j.api.util.SqlLogger.stdout());
        db = new Database(connection);
        users = db.createRepository(EnterpriseUser.class);
    }

    @AfterEach
    void tearDown() {
        db.close();
        new java.io.File(dbFile).delete();
    }

    @Test
    void testDefaultValue() {
        users.insert(new EnterpriseUser("test@example.com")).execute(connection).join();
        EnterpriseUser u = users.selectWhere("email = ?", "test@example.com").execute(connection).join().get(0);
        assertEquals("N/A", u.getBio());
    }

    @Test
    void testSoftDelete() {
        EnterpriseUser u1 = new EnterpriseUser("active@example.com");
        EnterpriseUser u2 = new EnterpriseUser("deleted@example.com");
        u2.setDeleted(true);

        users.insert(u1).execute(connection).join();
        users.insert(u2).execute(connection).join();

        // Standard select should hide deleted
        List<EnterpriseUser> active = users.selectAll().execute(connection).join();
        assertEquals(1, active.size());
        assertEquals("active@example.com", active.get(0).getEmail());

        // Select with deleted should show both
        List<EnterpriseUser> all = users.selectAll().withDeleted().execute(connection).join();
        assertEquals(2, all.size());
    }

    @Test
    void testDeleteMethod() {
        EnterpriseUser u = new EnterpriseUser("delete_me@example.com");
        users.insert(u).execute(connection).join();

        // Soft delete
        users.delete(u).execute(connection).join();

        // Should not be visible
        assertFalse(users.selectWhere("email = ?", "delete_me@example.com").execute(connection).join().stream().findFirst().isPresent());

        // Should be in DB
        EnterpriseUser dbUser = users.selectWhere("email = ?", "delete_me@example.com").withDeleted().execute(connection).join().get(0);
        assertTrue(dbUser.isDeleted());

        // Hard delete
        users.delete(dbUser).hardDelete().execute(connection).join();
        
        // Should be gone completely
        assertTrue(users.selectWhere("email = ?", "delete_me@example.com").withDeleted().execute(connection).join().isEmpty());
    }

    @Test
    void testUniqueConstraint() {
        users.insert(new EnterpriseUser("dup@example.com")).execute(connection).join();
        
        // Inserting same email should fail
        assertThrows(Exception.class, () -> {
            users.insert(new EnterpriseUser("dup@example.com")).execute(connection).join();
        });
    }
}
