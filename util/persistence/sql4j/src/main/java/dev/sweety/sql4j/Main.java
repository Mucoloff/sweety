package dev.sweety.sql4j;

import dev.sweety.sql4j.api.configuration.*;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.configuration.*;
import dev.sweety.sql4j.impl.connection.ConnectionType;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;

public class Main {

    @Table.Info(name = "users")
    public static class TestUser {
        @Column.Info(primaryKey = true, autoIncrement = true)
        private int id;

        @Column.Info
        private String name;

        @Column.Info
        private int age;

        public TestUser() {}

        public TestUser(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    public static void main(String[] args) {
        // SQLite with virtual threads (assuming Java 21+)
        var config = new SQLiteConfig("data.db");
        ConnectionType type = ConnectionType.SQLITE;
        // Fallback to cached thread pool if virtual threads not available or just demonstrate usage
        try (SqlConnection connection = type.create(config, Executors.newSingleThreadExecutor(), false);
             Database db = new Database(connection)) {

            Repository<TestUser> repo = db.createRepository(TestUser.class, "users");
            repo.create(db.dialect(), true).execute(connection).join();

            //TestUser user = new TestUser("Alice", 25);
            //db.transaction(tx -> tx.execute(repo.insert(user))).join();


            Query.execute(connection, "SELECT name FROM users").thenAccept(result -> {
                result.result().forEach(row -> {
                    Object name = row.get("name");
                    System.out.println("User name: " + name);
                });
            }).join();
        }
    }
}


