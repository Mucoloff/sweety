package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.Index;
import dev.sweety.sql4j.impl.BaseRepository;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.impl.connection.provider.HikariConnectionProvider;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.thread.ThreadUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaMigrationIndexTest {

    private SqlConnection connection;
    private String dbName;

    @Table.Info(name = "migration_test_entity")
    @Index(name = "idx_mig_composite", columns = {"first_name", "last_name"})
    public static class MigrationTestEntity {
        @Column.Info(name = "id", primaryKey = true)
        private int id;

        @Column.Info(name = "first_name")
        private String firstName;

        @Column.Info(name = "last_name")
        private String lastName;

        @Column.Info(name = "email")
        @Index(name = "idx_mig_email", unique = true)
        private String email;
    }

    @BeforeEach
    public void setUp() {
        dbName = "migration_test_" + System.nanoTime() + ".db";
        connection = ConnectionType.SQLITE.create(ThreadUtil.singleThreadScheduler("sql4j-test"), dbName);
    }

    @AfterEach
    public void tearDown() throws Exception {
        new java.io.File(dbName).delete();
    }

    @Test
    public void testMigrateSchemaCreatesMissingIndices() throws Exception {
        dev.sweety.sql4j.api.obj.table.TableRegistry registry = new dev.sweety.sql4j.api.obj.table.TableRegistry();
        Table<MigrationTestEntity> table = registry.get(MigrationTestEntity.class);
        BaseRepository<MigrationTestEntity> repo = new BaseRepository<>(table, connection.dialect(), new QueryCache(), registry, null);

        // 1. Create table without indices first
        connection.executeAsync(repo.createTable()).join();

        // 2. Run migrateSchema which should detect and create the missing indices
        repo.migrateSchema(connection);

        // 3. Verify via JDBC DatabaseMetaData that indices exist
        Set<String> indicesFound = new HashSet<>();
        try (Connection c = connection.connection()) {
            DatabaseMetaData metaData = c.getMetaData();
            try (ResultSet rs = metaData.getIndexInfo(null, null, "migration_test_entity", false, false)) {
                while (rs.next()) {
                    String idxName = rs.getString("INDEX_NAME");
                    if (idxName != null) {
                        indicesFound.add(idxName.toLowerCase());
                    }
                }
            }
        }

        assertTrue(indicesFound.contains("idx_mig_composite"), "Expected idx_mig_composite to be created by migrateSchema, found: " + indicesFound);
        assertTrue(indicesFound.contains("idx_mig_email"), "Expected idx_mig_email to be created by migrateSchema, found: " + indicesFound);
    }
}
