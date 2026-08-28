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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Measures row-hydration cost on a wide (14-column, mixed primitive/boxed/String) entity —
 * targets {@code SelectEntity.mapRow}'s per-column {@code ResultSet.getObject} boxing and the
 * generated dispatcher's {@code get_/set_} → cached-{@code Field} path (see
 * {@link DispatcherAccessBenchmark} for the isolated field-access cost). {@link BenchItem} used
 * elsewhere in this module is too narrow (3 columns) to show hydration cost distinctly from
 * JDBC round-trip overhead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class HydrationBenchmark {

    @Param({"1", "100", "1000"})
    int rowCount;

    private Database db;
    private SqlConnection con;
    private Repository<WideItem> items;
    private Integer firstId;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String dbPath = "mem:hydrate_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        db = SQL4J.connect().h2(dbPath, "sa", "").withCache(false).open();
        con = db.getConnection();
        items = db.createRepository(WideItem.class);
        items.createTable().execute(con).join();

        for (int i = 0; i < rowCount; i++) {
            WideItem w = WideItem.sample(i);
            items.insert(w).execute(con).join();
            if (i == 0) firstId = w.getId();
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try { items.dropTable().execute(con).join(); } catch (Exception ignored) {}
        db.close();
    }

    /** Hydrates a single wide row by primary key — isolates per-row mapRow cost. */
    @Benchmark
    public void selectByPk(Blackhole bh) {
        WideItem item = items.pk(firstId).find().execute(con).join();
        bh.consume(item);
    }

    /** Hydrates {@code rowCount} wide rows — mapRow cost multiplied across a full scan. */
    @Benchmark
    public void selectAll(Blackhole bh) {
        List<WideItem> all = items.select().execute(con).join();
        bh.consume(all);
    }
}
