package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.annotation.Sql4jRepository;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.chain.QueryChain;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class SQL4JRepositoryTest {

    private Database db;
    private SqlConnection sqlCon;
    private Connection con;
    private String dbPath;

    @Sql4jRepository(entity = User.class)
    public interface UserRepository extends Repository<User> {
        @dev.sweety.sql4j.api.annotation.Query("SELECT * FROM full_users WHERE age > ?")
        Query<List<User>> findOlderThan(int age);
        
        @dev.sweety.sql4j.api.annotation.Query("SELECT * FROM full_users WHERE name = ?")
        Query<List<User>> findByName(String name);
    }

    @BeforeEach
    public void setup() throws Exception {
        dbPath = "test_repo_" + System.nanoTime() + ".db";
        sqlCon = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), dbPath);
        con = sqlCon.connection();
        db = new Database(sqlCon);
        
        UserRepository repo = db.getCustomRepository(UserRepository.class);
        repo.createTable().execute(sqlCon).join();
        
        repo.insert(new UserTable.Builder().name("Alice").age(25).role(Role.USER).build()).execute(sqlCon).join();
        repo.insert(new UserTable.Builder().name("Bob").age(30).role(Role.USER).build()).execute(sqlCon).join();
        repo.insert(new UserTable.Builder().name("Charlie").age(35).role(Role.ADMIN).build()).execute(sqlCon).join();
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() throws Exception {
        if (sqlCon != null) sqlCon.close();
        Files.deleteIfExists(Path.of(dbPath));
    }

    @Test
    public void testCustomQuery() throws Exception {
        UserRepository repo = db.getCustomRepository(UserRepository.class);
        
        List<User> olderThan28 = repo.findOlderThan(28).execute(sqlCon).join();
        assertEquals(2, olderThan28.size());
        assertTrue(olderThan28.stream().anyMatch(u -> u.getName().equals("Bob")));
        assertTrue(olderThan28.stream().anyMatch(u -> u.getName().equals("Charlie")));
        
        List<User> bob = repo.findByName("Bob").execute(sqlCon).join();
        assertEquals(1, bob.size());
        assertEquals("Bob", bob.getFirst().getName());
    }

    @Test
    public void testStandardDSL() throws Exception {
        UserRepository repo = db.getCustomRepository(UserRepository.class);
        
        List<User> all = repo.select().execute(sqlCon).join();
        assertEquals(3, all.size());
    }
}
