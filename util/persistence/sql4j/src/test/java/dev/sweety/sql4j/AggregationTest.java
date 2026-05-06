package dev.sweety.sql4j;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.query.Aggregate;
import dev.sweety.sql4j.entity.User;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AggregationTest extends BaseSQLTest {

    private Repository<User> userRepo;

    @BeforeEach
    void setUp() {
        Database db = createDatabase();
        userRepo = db.createRepository(User.class);
        userRepo.create(true).execute(getConnection()).join();
        
        // Insert some data
        userRepo.insert(new User(1, "Alice", 25)).execute(getConnection()).join();
        userRepo.insert(new User(2, "Bob", 30)).execute(getConnection()).join();
        userRepo.insert(new User(3, "Charlie", 25)).execute(getConnection()).join();
        userRepo.insert(new User(4, "David", 35)).execute(getConnection()).join();
    }

    @Test
    void testCountGroupByAge() {
        SqlConnection con = getConnection();
        
        List<Row> results = userRepo.select(User.AGE, Aggregate.count(User.ID))
                .groupBy(User.AGE)
                .orderBy("age", true)
                .executeAggregate(con)
                .join();

        assertEquals(3, results.size());
        
        // Age 25: 2 users
        assertEquals(25, results.get(0).get("age"));
        assertEquals(2L, ((Number)results.get(0).get("COUNT(id)")).longValue());

        // Age 30: 1 user
        assertEquals(30, results.get(1).get("age"));
        assertEquals(1L, ((Number)results.get(1).get("COUNT(id)")).longValue());
    }

    @Test
    void testHavingClause() {
        SqlConnection con = getConnection();
        
        List<Row> results = userRepo.select(User.AGE, Aggregate.count(User.ID))
                .groupBy(User.AGE)
                .having(Aggregate.count(User.ID).gt(1))
                .executeAggregate(con)
                .join();

        assertEquals(1, results.size());
        assertEquals(25, results.get(0).get("age"));
        assertEquals(2L, ((Number)results.get(0).get("COUNT(id)")).longValue());
    }
}
