package dev.sweety.sql4j.benchmark;

import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.benchmark.entity.WideItem;
import dev.sweety.sql4j.impl.Database;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates a dashboard/API workload: many concurrent clients hitting one shared
 * {@link Database}, mostly reading (widgets polling live data), a minority writing (events
 * landing). Every prior benchmark in this module ran single-threaded — none of them show
 * whether the connection pool, {@link dev.sweety.sql4j.impl.cache.EntityCache}, or the
 * (now {@code CopyOnWriteArrayList}-backed) interceptor lists hold up under real contention.
 * {@link SqlConnection#executeAsync} borrows a fresh JDBC connection per call (see class doc),
 * so this is also the benchmark that actually stresses the Hikari pool's {@code maxPoolSize}.
 *
 * <p>{@code @Threads} is fixed per method (JMH requirement) at two levels approximating a
 * small dashboard (8 concurrent panels) and a busier one (32) — both well within Hikari's
 * default {@code maxPoolSize=10} (see {@code SQL4J.java:100-101}), so {@code dashboard32} in
 * particular is expected to show connection-acquisition queueing, not just query cost.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class DashboardLoadBenchmark {

    private Database db;
    private SqlConnection con;
    private Repository<WideItem> items;
    private int seedRowCount;
    private final AtomicInteger writeCounter = new AtomicInteger();

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String dbPath = "mem:dashboard_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        db = SQL4J.connect()
                .h2(dbPath, "sa", "")
                .pool(t -> t.maxPoolSize(10).minIdle(10))
                .open();
        con = db.getConnection();
        items = db.createRepository(WideItem.class);
        items.createTable().execute(con).join();

        seedRowCount = 500;
        for (int i = 0; i < seedRowCount; i++) {
            items.insert(WideItem.sample(i)).execute(con).join();
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try { items.dropTable().execute(con).join(); } catch (Exception ignored) {}
        db.close();
    }

    /** ~80/20 read/write mix, like a dashboard polling widgets while a minority of clients
     * submit new events — 8 concurrent clients, well under the 10-connection pool. */
    @Benchmark
    @Threads(8)
    public void dashboardMixed8(Blackhole bh) {
        mixedRequest(bh);
    }

    /** Same mix at 32 concurrent clients against the same 10-connection pool — exercises
     * connection-acquisition contention, not just query cost. */
    @Benchmark
    @Threads(32)
    public void dashboardMixed32(Blackhole bh) {
        mixedRequest(bh);
    }

    private void mixedRequest(Blackhole bh) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextInt(100) < 80) {
            int pk = rnd.nextInt(1, seedRowCount + 1);
            WideItem item = items.pk(pk).find().execute(con).join();
            bh.consume(item);
        } else {
            WideItem fresh = WideItem.sample(1_000_000 + writeCounter.incrementAndGet());
            items.insert(fresh).execute(con).join();
            bh.consume(fresh.getId());
        }
    }

    /** Pure concurrent read fan-out (no writes) — isolates read-path contention (EntityCache,
     * connection pool) from the write path. */
    @Benchmark
    @Threads(32)
    public void concurrentReadOnly32(Blackhole bh) {
        int pk = ThreadLocalRandom.current().nextInt(1, seedRowCount + 1);
        WideItem item = items.pk(pk).find().execute(con).join();
        bh.consume(item);
    }

    /** Pure concurrent select-all fan-out — every thread scans the full seeded table
     * concurrently, worst case for row-hydration + connection contention together. */
    @Benchmark
    @Threads(32)
    public void concurrentScanAll32(Blackhole bh) {
        List<WideItem> all = items.select().execute(con).join();
        bh.consume(all);
    }
}
