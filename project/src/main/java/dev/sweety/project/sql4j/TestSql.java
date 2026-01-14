package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.chain.DependentQueryChain;
import dev.sweety.sql4j.api.query.chain.SimpleQueryChain;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.transaction.TransactionManager;

import java.util.Collection;

public class TestSql {

    final Database database = new Database(ConnectionType.SQLITE, "test.db");

    final Repository<User> userRepository;
    final Repository<Order> orderRepository;
    final Repository<Product> productRepository;
    final Repository<OrderDetail> orderDetailRepository;

    TestSql() {
        this.userRepository = this.database.createRepository(User.class);
        this.orderRepository = this.database.createRepository(Order.class);
        this.productRepository = this.database.createRepository(Product.class);
        this.orderDetailRepository = this.database.createRepository(OrderDetail.class);
    }

    void run() throws Throwable {
        final Dialect dialect = this.database.dialect();
        final SqlConnection connection = database.getConnection();
        final Collection<Repository<?>> repositories = this.database.repositories();

        for (Repository<?> repository : repositories) repository.create(dialect, true).execute(connection).join();

        User alice = new User("alice", "alice.alice@gmail.com");
        Product iphone15 = new Product("iPhone 15", 599.99f, 100);
        Order order1 = new Order(alice.getId(), "pending");
        OrderDetail detail1 = new OrderDetail(order1.getId(), iphone15.getId(), 2);

        User giacomo = new User("giacomo", "giacomo.giacomo@gmail.com");
        Product macbookPro = new Product("MacBook Pro", 1999.99f, 50);
        Order order2 = new Order(giacomo.getId(), "shipped");
        OrderDetail detail2 = new OrderDetail(order2.getId(), macbookPro.getId(), 1);

        User tommy = new User("tommy", "tommy.tommy@gmail.com");
        Product ipadAir = new Product("iPad Air", 499.99f, 75);
        Order order3 = new Order(tommy.getId(), "delivered");
        OrderDetail detail3 = new OrderDetail(order3.getId(), ipadAir.getId(), 3);


        TransactionManager tm = new TransactionManager(connection);

        tm.transaction(tx -> {
                    long userId = tx.execute(userRepository.insert(alice)).value().getId();
                    long productId = tx.execute(productRepository.insert(iphone15)).value().getId();
                    order1.setUserId(userId);
                    long orderId = tx.execute(orderRepository.insert(order1)).value().getId();
                    detail1.setOrderId(orderId);
                    detail1.setProductId(productId);
                    tx.execute(orderDetailRepository.insert(detail1));
                }).thenRun(() -> System.out.println("Transazione completata per alice"))
                .exceptionally(ex -> {
                    System.out.println("transaction fallita per alice");
                    System.err.println("Rollback eseguito: " + ex.getMessage());
                    return null;
                }).join();



        tm.transaction(SimpleQueryChain.start(() -> userRepository.insert(giacomo))
                        .then(() -> {
                            order2.setUserId(giacomo.getId());
                            return orderRepository.insert(order2);
                        })
                        .then(() -> productRepository.insert(macbookPro))
                        .then(() -> {
                            detail2.setOrderId(order2.getId());
                            detail2.setProductId(macbookPro.getId());
                            return orderDetailRepository.insert(detail2);
                        })
                ).thenRun(() -> System.out.println("Transazione completata per giacomo"))
                .exceptionally(ex -> {
                    System.out.println("transaction fallita per giacomo");
                    System.err.println("Rollback eseguito: " + ex.getMessage());
                    return null;
                }).join();

        tm.transaction(
                        DependentQueryChain.start(userRepository.insert(tommy))
                                .then(v -> {
                                    long id = v.value().getId();
                                    order3.setUserId(id);
                                    return productRepository.insert(ipadAir);
                                })
                                .then(v -> {
                                    long id = v.value().getId();
                                    detail3.setProductId(id);
                                    return orderRepository.insert(order3);
                                }).then(v -> {
                                    long id = v.value().getId();
                                    detail3.setOrderId(id);
                                    return orderDetailRepository.insert(detail3);
                                })

                ).thenRun(() -> System.out.println("Transazione completata per tommy"))
                .exceptionally(ex -> {
                    System.out.println("transaction fallita per tommy");
                    System.err.println("Rollback eseguito: " + ex.getMessage());
                    return null;
                }).join();


        //for (Repository<?> repository : repositories.reversed()) repository.dropTable().execute(connection).join();

        SqlConnection.shutdownExecutor();
    }

    void printAll(Repository<?> repository) {
        repository.selectAll().execute(database.getConnection()).thenAccept(users -> {
            System.out.println(repository.table().name() + " in database:");
            users.forEach(System.out::println);
        }).join();
    }

    public static void main(String[] args) throws Throwable {
        new TestSql().run();
    }

}
