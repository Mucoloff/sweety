package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.Index;
import dev.sweety.sql4j.api.obj.annotation.Indexes;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SQL4JIndexTest {

    @Table.Info(name = "indexed_users")
    @Index(name = "idx_custom_composite", columns = {"first_name", "last_name"}, unique = false)
    @Index(columns = {"email", "tenant_id"}, unique = true)
    public static class IndexedUser {
        @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
        private int id;

        @Column.Info(name = "email")
        private String email;

        @Column.Info(name = "tenant_id")
        private int tenantId;

        @Column.Info(name = "first_name")
        private String firstName;

        @Column.Info(name = "last_name")
        private String lastName;

        @Column.Info(name = "phone")
        @Index(name = "idx_user_phone", unique = true)
        private String phone;

        @Column.Info(name = "status")
        @Index
        private String status;
    }

    @Test
    public void testTableIndexParsing() {
        TableRegistry registry = new TableRegistry();
        Table<IndexedUser> table = registry.get(IndexedUser.class);

        assertNotNull(table);
        List<Table.IndexDef> indices = table.indices();
        assertNotNull(indices);
        assertFalse(indices.isEmpty());

        // We expect:
        // 1. idx_user_phone (from phone field)
        // 2. idx_indexed_users_status (from status field)
        // 3. idx_custom_composite on [first_name, last_name]
        // 4. idx_indexed_users_email_tenant_id on [email, tenant_id] (unique)

        assertTrue(indices.stream().anyMatch(i -> i.name().equals("idx_user_phone") && i.unique() && i.columns().equals(List.of("phone"))));
        assertTrue(indices.stream().anyMatch(i -> i.name().equals("idx_indexed_users_status") && !i.unique() && i.columns().equals(List.of("status"))));
        assertTrue(indices.stream().anyMatch(i -> i.name().equals("idx_custom_composite") && !i.unique() && i.columns().equals(List.of("first_name", "last_name"))));
        assertTrue(indices.stream().anyMatch(i -> i.name().equals("idx_indexed_users_email_tenant_id") && i.unique() && i.columns().equals(List.of("email", "tenant_id"))));
    }

    @Test
    public void testCreateTableIndexDdlGeneration() {
        TableRegistry registry = new TableRegistry();
        Table<IndexedUser> table = registry.get(IndexedUser.class);

        Dialect h2Dialect = DialectType.H2.dialect();
        List<String> ddlList = CreateTable.buildIndices(table, h2Dialect, true);

        assertNotNull(ddlList);
        assertEquals(4, ddlList.size());

        // Check composite DDL structure
        assertTrue(ddlList.stream().anyMatch(sql -> sql.contains("CREATE INDEX IF NOT EXISTS idx_custom_composite ON indexed_users (first_name, last_name)")));
        assertTrue(ddlList.stream().anyMatch(sql -> sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS idx_indexed_users_email_tenant_id ON indexed_users (email, tenant_id)")));
        assertTrue(ddlList.stream().anyMatch(sql -> sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS idx_user_phone ON indexed_users (phone)")));
        assertTrue(ddlList.stream().anyMatch(sql -> sql.contains("CREATE INDEX IF NOT EXISTS idx_indexed_users_status ON indexed_users (status)")));
    }
}
