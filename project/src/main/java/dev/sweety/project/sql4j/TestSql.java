package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.ConnectionType;
import dev.sweety.sql4j.impl.query.InsertQuery;
import dev.sweety.sql4j.impl.query.util.QueryBuilder;
import dev.sweety.sql4j.impl.query.util.QueryUtil;

public class TestSql {


    public static void main(String[] args) throws Throwable {

        SqlConnection connection = ConnectionType.SQLITE.getConnection("test.db");

        QueryUtil.execute(connection, "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT)",
                        ps -> {
                        },
                        ps -> {
                            ps.execute();
                            return "affected rows: " + ps.getUpdateCount();
                        }).thenAccept(b -> System.out.println("Table created successfully: " + b))
                .exceptionally(ex -> {
                    ex.printStackTrace(System.err);
                    return null;
                }).join();

        QueryBuilder<User> queryBuilder = new QueryBuilder<>(User.class);

        User u1 = new User("a", "b");
        User u2 = new User("c", "d");

        InsertQuery<User> insert = queryBuilder.insert(u1);

        connection.executeAsync(insert).thenAccept(r -> System.out.println("inserting user completed: " + r)).exceptionally(ex -> {
            ex.printStackTrace(System.err);
            return null;
        }).join();

        System.out.println("u1: " + u1);

        connection.executeAsync(queryBuilder.insert(u2)).join();

        System.out.println("u2: " + u2);

        connection.close();
        SqlConnection.shutdownExecutor();
    }

}
