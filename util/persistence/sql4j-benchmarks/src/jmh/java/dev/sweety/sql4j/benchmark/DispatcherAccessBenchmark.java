package dev.sweety.sql4j.benchmark;

import dev.sweety.sql4j.api.obj.TableAccessor;
import dev.sweety.sql4j.benchmark.entity.BenchItem;
import dev.sweety.sql4j.benchmark.entity.BenchItemTable;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH port of the old hand-rolled {@code SQL4JBenchmarkTest} nanoTime loop — same comparison
 * (generated mirror dispatcher vs raw reflective {@code Field.set}), now with proper JMH
 * warmup/forking instead of a single-shot System.nanoTime measurement. Both paths ultimately
 * call {@code Field.set} under the hood (see {@code SQL4JProcessor.buildDispatcher}) — this
 * isolates the string-switch + method-call overhead the generated dispatcher adds on top.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class DispatcherAccessBenchmark {

    private BenchItem item;
    private TableAccessor<BenchItem> generatedAccessor;
    private Map<String, Field> reflectiveFields;

    @Setup(Level.Trial)
    public void setup() {
        item = new BenchItem();
        generatedAccessor = BenchItemTable.INSTANCE;
        reflectiveFields = new HashMap<>();
        for (Field f : BenchItem.class.getDeclaredFields()) {
            f.setAccessible(true);
            reflectiveFields.put(f.getName(), f);
        }
    }

    /** Generated mirror dispatcher: string switch -> static set_name -> cached Field.set. */
    @Benchmark
    public void generatedDispatcherSet(Blackhole bh) {
        generatedAccessor.setFieldValue(item, "name", "Charlie");
        bh.consume(item);
    }

    /** Raw reflection: HashMap<String,Field> lookup -> Field.set, no dispatcher indirection. */
    @Benchmark
    public void reflectiveFieldSet(Blackhole bh) throws Exception {
        reflectiveFields.get("name").set(item, "Charlie");
        bh.consume(item);
    }
}
