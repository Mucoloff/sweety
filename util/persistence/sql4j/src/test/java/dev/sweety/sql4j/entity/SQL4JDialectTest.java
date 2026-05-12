package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.query.entity.SelectEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SQL4JDialectTest {

    @Test
    @DisplayName("Dialect: SQLite Escaping & UPSERT")
    void testSqlite() {
        Dialect dialect = DialectType.SQLITE.dialect();
        assertEquals("\"user\"", dialect.escape("user"));
        
        String upsert = dialect.upsertSyntax("user", List.of("id", "name"), List.of("name"), List.of("id"));
        assertEquals("INSERT INTO \"user\" (\"id\", \"name\") VALUES (?, ?) ON CONFLICT (\"id\") DO UPDATE SET \"name\" = excluded.\"name\"", upsert);
    }

    @Test
    @DisplayName("Dialect: Postgres Escaping & UPSERT")
    void testPostgres() {
        Dialect dialect = DialectType.POSTGRESQL.dialect();
        assertEquals("\"user\"", dialect.escape("user"));
        
        String upsert = dialect.upsertSyntax("user", List.of("id", "name"), List.of("name"), List.of("id"));
        assertEquals("INSERT INTO \"user\" (\"id\", \"name\") VALUES (?, ?) ON CONFLICT (\"id\") DO UPDATE SET \"name\" = EXCLUDED.\"name\"", upsert);
    }

    @Test
    @DisplayName("Dialect: MySQL Escaping & UPSERT")
    void testMySql() {
        Dialect dialect = DialectType.MYSQL.dialect();
        assertEquals("`user`", dialect.escape("user"));
        
        String upsert = dialect.upsertSyntax("user", List.of("id", "name"), List.of("name"), List.of("id"));
        assertEquals("INSERT INTO `user` (`id`, `name`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`)", upsert);
    }

    @Test
    @DisplayName("Dialect: Limit/Offset Syntax")
    void testLimitOffset() {
        Dialect sqlite = DialectType.SQLITE.dialect();
        assertEquals(" LIMIT 10 OFFSET 20", sqlite.limitOffsetSyntax(10, 20));
        
        Dialect postgres = DialectType.POSTGRESQL.dialect();
        assertEquals(" LIMIT 10 OFFSET 20", postgres.limitOffsetSyntax(10, 20));
    }

    @Test
    @DisplayName("SelectQuery: Dialect-Aware SQL")
    void testSelectQueryDialect() {
        TableRegistry registry = TableRegistry.getDefault();
        Table<User> table = registry.get(User.class);
        
        QueryCache cache = new QueryCache();
        
        // SQLite
        SelectEntity<User> selectSqlite = 
            new SelectEntity<>(table, cache, DialectType.SQLITE.dialect(), registry);
        String sqlSqlite = selectSqlite.limit(10).sql();
        System.out.println("SQLITE: " + sqlSqlite);
        assertTrue(sqlSqlite.contains("SELECT"), "Should be a SELECT");
        assertTrue(sqlSqlite.contains("\"id\""), "Should have escaped id");
        assertTrue(sqlSqlite.contains("\"full_users\""), "Should have escaped table name");
        assertTrue(sqlSqlite.contains("LIMIT 10"), "Should have LIMIT 10");

        // MySQL
        SelectEntity<User> selectMysql = 
            new SelectEntity<>(table, cache, DialectType.MYSQL.dialect(), registry);
        String sqlMysql = selectMysql.limit(10).sql();
        System.out.println("MYSQL: " + sqlMysql);
        assertTrue(sqlMysql.contains("SELECT"), "Should be a SELECT");
        assertTrue(sqlMysql.contains("`id`"), "Should have backtick escaped id");
        assertTrue(sqlMysql.contains("`full_users`"), "Should have backtick escaped table name");
        assertTrue(sqlMysql.contains("LIMIT 10"), "Should have LIMIT 10");
    }
}
