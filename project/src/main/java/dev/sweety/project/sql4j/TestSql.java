package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.ConnectionType;
import dev.sweety.sql4j.impl.query.param.QueryResult;
import dev.sweety.sql4j.impl.query.util.QueryBuilder;

import java.util.Map;

public class TestSql {

    final SqlConnection connection = ConnectionType.SQLITE.getConnection("test.db");
    final QueryBuilder<User> queryBuilder = new QueryBuilder<>(User.class);

    /* todo
    * foreign key support
    *   relationships (one-to-one, one-to-many, many-to-many)
    * partial keys (ppk, pfk)
    *
    * table-joins
    * */

    void run() throws Throwable {

        queryBuilder.create(connection.dialect(), true).execute(connection)

                /*
                QueryBuilder.execute(connection, "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT)",
                        QueryExecutor.EMPTY,
                        ps -> {
                            ps.execute();
                            return "affected rows: " + ps.getUpdateCount();
                        })
                */

        .thenAccept(b -> System.out.println("Table created successfully: " + b))
                .exceptionally(ex -> {
                    ex.printStackTrace(System.err);
                    return null;
                }).join();

        User u1 = new User("a", "b");
        User u2 = new User("c", "d");

        queryBuilder.insert(u1).execute(connection).thenAccept(r -> System.out.println("inserting user completed: " + r)).exceptionally(ex -> {
            ex.printStackTrace(System.err);
            return null;
        }).join();

        System.out.println("u1: " + u1);

        queryBuilder.insert(u2).execute(connection).join();

        System.out.println("u2: " + u2);
        System.out.println("-------------------");

        queryBuilder.selectWhere("id = ?", u1.getId())
                .execute(connection)
                .thenAccept(user -> System.out.println("Selected user: " + user))
                .join();

        printAll();

        queryBuilder.delete(u2).execute(connection).join();

        printAll();

        QueryBuilder.execute(connection, "SELECT * FROM users WHERE id = ?",
                        ps -> ps.setObject(1, u1.getId()),
                QueryResult::fromStatement).thenAccept(r -> {
            boolean b = r.hasResultSet();
            boolean b1 = r.hasGeneratedKeys();
            System.out.println("hasResultSet: " + b);

            if (b){
                System.out.println("resultset");
                for (Map<String, Object> map : r.result()) {
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        System.out.println(entry.getKey() + ": " + entry.getValue());
                    }
                }
            }

            System.out.println("hasGeneratedKeys: " + b1);
            if (b1){
                System.out.print("generatedKeys: [");
                for (Integer generatedKey : r.generatedKeys()) {
                    System.out.printf("%d ", generatedKey);
                }
                System.out.println("]");
            }

        }).join();

        connection.close();
        SqlConnection.shutdownExecutor();
    }

    void printAll() {
        queryBuilder.selectAll().execute(connection).thenAccept(users -> {
            System.out.println("Users in database:");
            users.forEach(System.out::println);
        }).join();
    }

    public static void main(String[] args) throws Throwable {
        new TestSql().run();
    }

}
