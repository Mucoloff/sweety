package dev.sweety.sql4j.benchmark;

import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.benchmark.entity.BenchItem;
import dev.sweety.sql4j.impl.Database;
import org.openjdk.jmh.annotations.*;

/**
 * JMH state shared by {@link InsertThroughputBenchmark} and {@link CacheHitVsMissBenchmark}.
 * Creates an H2 in-memory database once per benchmark trial and tears it down afterwards.
 */
@State(Scope.Benchmark)
public class BenchmarkState {

    Database db;
    SqlConnection con;
    Repository<BenchItem> items;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String dbPath = "mem:bench_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        db = SQL4J.connect().h2(dbPath, "sa", "").open();
        con = db.getConnection();
        items = db.createRepository(BenchItem.class);
        items.createTable().execute(con).join();
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try { items.dropTable().execute(con).join(); } catch (Exception ignored) {}
        db.close();
    }

    /** Clears the table between iterations to avoid unbounded row growth. */
    @Setup(Level.Iteration)
    public void resetTable() {
        items.dropTable().execute(con).join();
        items.createTable().execute(con).join();
    }
}
