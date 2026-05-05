package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.configuration.SQLiteConfig;
import dev.sweety.sql4j.impl.connection.ConnectionType;

import java.util.concurrent.Executors;

/**
 * Demo / smoke-test entrypoint. Not a JUnit test.
 * Shows end-to-end usage of sql4j: insert, select, join, raw query.
 */
public class Main {

    public static void main(String[] args) {
        var config = new SQLiteConfig("data.db");
        ConnectionType type = ConnectionType.SQLITE;

        try (SqlConnection connection = type.create(config, false);
             Database db = new Database(connection)) {

            Repository<TestUser>  users  = db.createRepository(TestUser.class);
            Repository<TestData>  datas  = db.createRepository(TestData.class);
            Repository<TestOrder> orders = db.createRepository(TestOrder.class);

            TestUser user = new TestUser("Alice", 25);
            TestData data = new TestData("data1");

            // Insert in a transaction with savepoint demo
            db.transact(tx -> {
                var testUser  = tx.execute(users.insert(user)).entity();
                var testData  = tx.execute(datas.insert(data)).entity();
                var order     = tx.execute(orders.insert(new TestOrder(testUser, testData))).entity();
                System.out.println("Inserted User:  " + testUser);
                System.out.println("Inserted Data:  " + testData);
                System.out.println("Inserted Order: " + order);
            }).join();

            // Raw parameterised query → List<Row>
            Query.execute(connection, "SELECT name FROM users").thenAccept(result ->
                result.result().forEach(row -> System.out.println("User name: " + row.getString("name")))
            ).join();

            // SelectAll
            orders.selectAll().execute(connection).join().forEach(System.out::println);

            System.out.println("-----------------");

            // JOIN query → List<Row>
            Query.join(users.table(), datas.table(), orders.table())
                    .on("users.id = orders.userId")
                    .on("datas.id = orders.dataId")
                    .where("users.age > ?", 20)
                    .build()
                    .execute(connection)
                    .join()
                    .forEach(System.out::println);
        }
    }
}
