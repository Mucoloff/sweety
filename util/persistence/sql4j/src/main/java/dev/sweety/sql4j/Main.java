package dev.sweety.sql4j;

import dev.sweety.sql4j.api.configuration.*;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.ForeignKey;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.chain.SimpleQueryChain;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.configuration.*;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.query.SelectJoin;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
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

        public TestUser() {
        }

        public TestUser(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    @Table.Info(name = "datas")
    public static class TestData {

        @Column.Info(primaryKey = true, autoIncrement = true)
        private int id;

        @Column.Info
        private String name;

        public TestData(String name) {
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @Table.Info(name = "orders")
    public static class TestOrder {
        @Column.Info(primaryKey = true, autoIncrement = true)
        private int id;

        @Column.Info
        @ForeignKey.Info(table = TestUser.class, column = "id", onDelete = ForeignKey.Action.CASCADE, onUpdate = ForeignKey.Action.CASCADE)
        private int userId;

        @Column.Info
        @ForeignKey.Info(table = TestData.class, column = "id", onDelete = ForeignKey.Action.CASCADE, onUpdate = ForeignKey.Action.CASCADE)
        private int dataId;

        public TestOrder() {

        }

        public TestOrder(TestUser user, TestData data) {
            this.userId = user.getId();
            this.dataId = data.getId();
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", TestOrder.class.getSimpleName() + "[", "]")
                    .add("id=" + id)
                    .add("userId=" + userId)
                    .add("dataId=" + dataId)
                    .toString();
        }
    }

    public static void main(String[] args) {
        // SQLite with virtual threads (assuming Java 21+)
        var config = new SQLiteConfig("data.db");
        ConnectionType type = ConnectionType.SQLITE;
        // Fallback to cached thread pool if virtual threads not available or just demonstrate usage
        try (SqlConnection connection = type.create(config, Executors.newSingleThreadExecutor(), false);
             Database db = new Database(connection)) {

            Repository<TestUser> users = db.createRepository(TestUser.class);
            Repository<TestData> datas = db.createRepository(TestData.class);
            Repository<TestOrder> orders = db.createRepository(TestOrder.class);

            TestUser user = new TestUser("Alice", 25);
            TestData data = new TestData("data1");

           /*
            db.transaction(
                    SimpleQueryChain.start(() -> users.insert(user))
                            .then(() -> datas.insert(data))
                            .then(() -> orders.insert(new TestOrder(user, data)))
            ).join();
            */


            Query.execute(connection, "SELECT name FROM users").thenAccept(result -> {
                result.result().forEach(row -> {
                    Object name = row.get("name");
                    System.out.println("User name: " + name);
                });
            }).join();


            System.out.println(user);
            System.out.println(data);

            orders.selectAll().execute(connection).join().forEach(System.out::println);

            System.out.println("-----------------");

            Query.join(users.table(), datas.table(), orders.table())
                    .on("users.id = orders.userId")
                    .on("datas.id = orders.dataId")
                    .where("users.age > ?", 20)
                    .build().execute(connection).join().forEach(System.out::println);
        }
    }
}


