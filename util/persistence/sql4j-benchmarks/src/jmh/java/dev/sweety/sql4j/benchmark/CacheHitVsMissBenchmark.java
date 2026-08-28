package dev.sweety.sql4j.benchmark;

import dev.sweety.sql4j.benchmark.entity.BenchItem;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Compares L2 cache-hit latency against a full DB round-trip (cache miss).
 *
 * <h3>Methodology</h3>
 * <ol>
 *   <li>{@code cacheHit} — inserts a row, reads it (populates cache), then measures
 *       subsequent {@code pk(id).find()} calls that are served entirely from the
 *       Caffeine in-memory cache.</li>
 *   <li>{@code cacheMiss} — clears the entity cache between every call so that every
 *       {@code pk(id).find()} triggers a full JDBC round-trip to H2.</li>
 * </ol>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CacheHitVsMissBenchmark {

    private BenchmarkState state;
    private Integer cachedId;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        state = new BenchmarkState();
        state.setup();
        // Pre-insert a single item to use as the cache target
        BenchItem item = new BenchItem("cached", 1);
        state.items.insert(item).execute(state.con).join();
        cachedId = item.getId();
        // Warm the cache with a first read
        state.items.pk(cachedId).find().execute(state.con).join();
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        state.teardown();
    }

    /** L2 cache hit: result is returned directly from Caffeine without touching JDBC. */
    @Benchmark
    public void cacheHit(Blackhole bh) {
        BenchItem item = state.items.pk(cachedId).find().execute(state.con).join();
        bh.consume(item);
    }

    /** Cache miss: entity cache is cleared before each call, forcing a DB round-trip. */
    @Benchmark
    public void cacheMiss(Blackhole bh) {
        state.db.entityCache().clear();
        BenchItem item = state.items.pk(cachedId).find().execute(state.con).join();
        bh.consume(item);
    }
}
