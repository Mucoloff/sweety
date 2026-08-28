package dev.sweety.sql4j.benchmark;

import dev.sweety.sql4j.benchmark.entity.BenchItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Measures single-row and batched insert throughput on H2 in-memory.
 *
 * <h3>Baseline questions answered</h3>
 * <ul>
 *   <li>How many single inserts/second can SQL4J sustain against H2?</li>
 *   <li>How does {@code insertBatch()} scale at 100 / 1 000 rows per call?</li>
 * </ul>
 *
 * <h3>Running</h3>
 * <pre>
 *   ./gradlew :util:persistence:sql4j-benchmarks:jmh
 *       -Pjmh.benchmarks=InsertThroughputBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class InsertThroughputBenchmark {

    @Param({"100", "1000"})
    int batchSize;

    private BenchmarkState state;
    private List<BenchItem> batch;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        state = new BenchmarkState();
        state.setup();
    }

    @Setup(Level.Iteration)
    public void prepareBatch() {
        state.resetTable();
        batch = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            batch.add(new BenchItem("item-" + i, i));
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        state.teardown();
    }

    /** Single insert — one row per benchmark call. */
    @Benchmark
    public void singleInsert(Blackhole bh) {
        BenchItem item = new BenchItem("solo", 42);
        state.items.insert(item).execute(state.con).join();
        bh.consume(item.getId());
    }

    /** Batch insert — {@code batchSize} rows per benchmark call. */
    @Benchmark
    public void batchInsert(Blackhole bh) {
        int[] counts = state.items.insertBatch(batch).execute(state.con).join();
        bh.consume(counts);
    }
}
