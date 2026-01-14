package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.chain.DependentQueryChain;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.query.SelectJoin;
import dev.sweety.sql4j.impl.transaction.TransactionManager;

import java.util.List;
import java.util.Map;

public class TestSql {

    final SqlConnection connection = ConnectionType.SQLITE.getConnection("test.db");
    final Repository<User> userRepository = new Repository<>(User.class);
    final Repository<Order> orderRepository = new Repository<>(Order.class);
    final Repository<Product> productRepository = new Repository<>(Product.class);
    final Repository<OrderDetail> orderDetailRepository = new Repository<>(OrderDetail.class);

    void run() throws Throwable {
        Dialect dialect = connection.dialect();

        List<Repository<?>> repositories = List.of(
                userRepository,
                productRepository,
                orderRepository,
                orderDetailRepository
        );

        for (Repository<?> repository : repositories) repository.create(dialect, true).execute(connection).join();

        User alice = new User("alice", "alice.alice@gmail.com");
        User giacomo = new User("giacomo", "giacomo.giacomo@gmail.com");

        //userRepository.insert(alice).execute(connection).join();
        userRepository.insert(giacomo).execute(connection).join();
        printAll(userRepository);

        Product iphone15 = new Product("iPhone 15", 599.99f, 100);
        Product macbookPro = new Product("MacBook Pro", 1999.99f, 50);
        productRepository.insert(iphone15).execute(connection).join();
        //productRepository.insert(macbookPro).execute(connection).join();
        printAll(productRepository);

        Order order1 = new Order(alice.getId(), "pending");
        Order order2 = new Order(giacomo.getId(), "shipped");
        //orderRepository.insert(order1).execute(connection).join();
        orderRepository.insert(order2).execute(connection).join();
        printAll(orderRepository);

        OrderDetail detail1 = new OrderDetail(order1.getId(), iphone15.getId(), 2);
        OrderDetail detail2 = new OrderDetail(order2.getId(), macbookPro.getId(), 1);
        //orderDetailRepository.insert(detail1).execute(connection).join();
        orderDetailRepository.insert(detail2).execute(connection).join();
        printAll(orderDetailRepository);


        SelectJoin joinQuery = Repository.join(orderRepository.table(), orderDetailRepository.table(), productRepository.table())
                .on("orders.id = order_details.order_id",
                        "order_details.product_id = products.id")
                .where("orders.user_id = ?",
                        1)
                .build();

        joinQuery.execute(connection).thenAccept(rows -> {
            System.out.println("Joined data for order id 1:");
            for (Map<String, Object> row : rows) {
                System.out.println(row);
            }
        });


        TransactionManager tm = new TransactionManager(connection);

        tm.transaction(tx -> {
                    tx.execute(userRepository.insert(alice));
                    tx.execute(orderRepository.insert(order1));
                    tx.execute(productRepository.insert(macbookPro));
                    tx.execute(orderDetailRepository.insert(detail1));
                }).thenRun(() -> System.out.println("Transazione completata"))
                .exceptionally(ex -> {
                    System.err.println("Rollback eseguito: " + ex.getMessage());
                    return null;
                }).join();


        DependentQueryChain.start(
                userRepository.insert(alice) // ritorna userId
        ).then(userId ->
                orderRepository.insert(new Order(userId, "pending"))
        ).then(orderId ->
                orderDetailRepository.insert(new OrderDetail(orderId, 1, 2))
        ).execute(connection);


        //for (Repository<?> repository : repositories.reversed()) repository.dropTable().execute(connection).join();

        SqlConnection.shutdownExecutor();
    }

    void printAll(Repository<?> repository) {
        repository.selectAll().execute(connection).thenAccept(users -> {
            System.out.println(repository.table().name() + " in database:");
            users.forEach(System.out::println);
        }).join();
    }

    public static void main(String[] args) throws Throwable {
        new TestSql().run();
    }

}
