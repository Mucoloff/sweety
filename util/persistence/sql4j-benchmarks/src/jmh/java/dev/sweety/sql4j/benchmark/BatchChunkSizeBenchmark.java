package dev.sweety.sql4j.benchmark;

import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.benchmark.entity.BenchItem;
import dev.sweety.sql4j.impl.Database;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Identifies the optimal {@code batchChunkSize} for {@code insertBatch()} on H2.
 *
 * <p>Compares a fixed {@value ROWS}-row batch executed:
 * <ul>
 *   <li>unchunked (single {@code executeBatch()});</li>
 *   <li>in chunks of 100, 250, and 500 rows.</li>
 * </ul>
 *
 * <h3>Running</h3>
 * <pre>
 *   ./gradlew :util:persistence:sql4j-benchmarks:jmh
 *       -Djmh.benchmarks=BatchChunkSizeBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class BatchChunkSizeBenchmark {

    /** 0 = unchunked; other values configure the chunk size. */
    @Param({"0", "100", "250", "500"})
    int chunkSize;

    static final int ROWS = 1_000;

    private Database db;
    private SqlConnection con;
    private Repository<BenchItem> items;
    private List<BenchItem> batch;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String dbPath = "mem:chunk_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        db = SQL4J.connect()
                .h2(dbPath, "sa", "")
                .batchChunkSize(chunkSize)
                .open();
        con = db.getConnection();
        items = db.createRepository(BenchItem.class);
        items.createTable().execute(con).join();

        batch = new ArrayList<>(ROWS);
        for (int i = 0; i < ROWS; i++) {
            batch.add(new BenchItem("item-" + i, i));
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try { items.dropTable().execute(con).join(); } catch (Exception ignored) {}
        db.close();
    }

    @Setup(Level.Iteration)
    public void clearTable() {
        items.dropTable().execute(con).join();
        items.createTable().execute(con).join();
    }

    @Benchmark
    public void insertBatch(Blackhole bh) {
        int[] counts = items.insertBatch(batch).execute(con).join();
        bh.consume(counts);
    }
}
