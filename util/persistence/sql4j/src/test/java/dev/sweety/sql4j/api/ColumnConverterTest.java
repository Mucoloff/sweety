package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.ColumnConverter;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class ColumnConverterTest {

    public static class Base64Converter implements ColumnConverter {
        @Override
        public Object toDatabase(Object value) {
            if (value == null) return null;
            return Base64.getEncoder().encodeToString(value.toString().getBytes());
        }

        @Override
        public Object fromDatabase(Object value) {
            if (value == null) return null;
            return new String(Base64.getDecoder().decode(value.toString()));
        }
    }

    @Table.Info(name = "converted_users")
    public static class ConvertedUser {
        @Column.Info(name = "id", primaryKey = true)
        public Long id;

        @Column.Info(name = "secretEmail", converter = Base64Converter.class)
        public String secretEmail;

        public ConvertedUser() {}

        public ConvertedUser(Long id, String secretEmail) {
            this.id = id;
            this.secretEmail = secretEmail;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getSecretEmail() {
            return secretEmail;
        }

        public void setSecretEmail(String secretEmail) {
            this.secretEmail = secretEmail;
        }
    }

    private Database db;
    private SqlConnection con;
    private Repository<ConvertedUser> repo;
    private String dbPath;

    @BeforeEach
    public void setup() {
        dbPath = "test_converter_" + System.nanoTime() + ".db";
        con = ConnectionType.SQLITE.create(Executors.newSingleThreadExecutor(), dbPath);
        db = new Database(con);
        repo = db.createRepository(ConvertedUser.class);
        db.migrateAll();
    }

    @AfterEach
    public void teardown() {
        if (db != null) db.close();
        try {
            Files.deleteIfExists(Path.of(dbPath));
        } catch (Exception ignored) {}
    }

    @Test
    void testSymmetricConversionOnPersistAndHydrate() throws Exception {
        String originalEmail = "sensitive.user@luce.internal";
        ConvertedUser user = new ConvertedUser(101L, originalEmail);

        // 1. Insert via repository
        repo.insert(user).execute(con).join();

        // 2. Hydrate via repository -> must decrypt to originalEmail
        ConvertedUser fetched = repo.pk(101L).find().execute(con).join();
        assertNotNull(fetched);
        assertEquals(101L, fetched.id);
        assertEquals(originalEmail, fetched.secretEmail, "Converter must reverse transformation on read");

        // 3. Inspect raw SQLite storage -> column must contain base64 ciphertext
        try (Connection rawConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = rawConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT secretEmail FROM converted_users WHERE id = 101")) {
            assertTrue(rs.next());
            String rawDbValue = rs.getString(1);
            String expectedCipher = Base64.getEncoder().encodeToString(originalEmail.getBytes());
            assertEquals(expectedCipher, rawDbValue, "Raw DB value must be transformed by converter.toDatabase()");
            assertNotEquals(originalEmail, rawDbValue, "Raw DB value must not contain plaintext");
        }
    }
}
