package dev.sweety.sql4j.benchmark;

import com.zaxxer.hikari.HikariConfig;
import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.benchmark.entity.BenchItem;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Answers the question round 1 left open: does app-layer/driver-level PreparedStatement
 * caching actually help, on a dialect where it's real (MariaDB — connector/J's
 * {@code cachePrepStmts}/{@code useServerPrepStmts}, already wired on by
 * {@code HikariConnectionProvider.applyDialectTuning} for MYSQL/MARIADB)? Round 1's
 * {@code BatchChunkSizeBenchmark} was flat on H2, which has no such flag — inconclusive by
 * design. This drives the same repeated-prepare workload against a real MariaDB with the
 * cache toggled on ({@code psCacheOn}, luce's actual default config) vs off
 * ({@code psCacheOff}, a raw {@link HikariConfig} bypassing dialect tuning via
 * {@code SQL4J.connect()....withHikariConfig(...)}) to isolate the effect.
 *
 * <p>Requires a real MariaDB reachable at {@code -Dsql4j.bench.mariadb.host/port/user/pass/db}
 * (defaults match a local {@code docker run mariadb:11} / SSH-tunneled instance on
 * {@code 127.0.0.1:13306}, user {@code root}, password {@code benchpass}, db
 * {@code sql4jbench}) — skipped entirely (no-op {@code @Setup} throws a clear message) if
 * unreachable, so this never blocks the rest of the suite from running on H2 alone.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class MariaDbPsCacheBenchmark {

    private static final String HOST = System.getProperty("sql4j.bench.mariadb.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("sql4j.bench.mariadb.port", 13306);
    private static final String USER = System.getProperty("sql4j.bench.mariadb.user", "root");
    private static final String PASS = System.getProperty("sql4j.bench.mariadb.pass", "benchpass");
    private static final String DB_NAME = System.getProperty("sql4j.bench.mariadb.db", "sql4jbench");

    static final int ROWS = 500;

    private Database dbCacheOn;
    private Database dbCacheOff;
    private SqlConnection conOn;
    private SqlConnection conOff;
    private Repository<BenchItem> itemsOn;
    private Repository<BenchItem> itemsOff;
    private List<BenchItem> preparedInserts;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // ON: luce's real default path — HikariConnectionProvider.applyDialectTuning wires
        // cachePrepStmts=true/useServerPrepStmts=true for MARIADB automatically.
        dbCacheOn = SQL4J.connect().mariadb(HOST, PORT, DB_NAME, USER, PASS).open();
        conOn = dbCacheOn.getConnection();
        itemsOn = dbCacheOn.createRepository(BenchItem.class, "bench_items_ps_on");
        itemsOn.createTable().execute(conOn).join();

        // OFF: raw HikariConfig bypassing dialect tuning entirely (SQL4J's documented
        // power-user escape hatch) — same MariaDB, same driver, PS caching explicitly disabled.
        HikariConfig off = new HikariConfig();
        off.setJdbcUrl("jdbc:mariadb://" + HOST + ":" + PORT + "/" + DB_NAME);
        off.setUsername(USER);
        off.setPassword(PASS);
        off.addDataSourceProperty("cachePrepStmts", "false");
        off.addDataSourceProperty("useServerPrepStmts", "false");
        dbCacheOff = SQL4J.connect().mariadb(HOST, PORT, DB_NAME, USER, PASS).withHikariConfig(off).open();
        conOff = dbCacheOff.getConnection();
        itemsOff = dbCacheOff.createRepository(BenchItem.class, "bench_items_ps_off");
        itemsOff.createTable().execute(conOff).join();

        preparedInserts = new ArrayList<>(ROWS);
        for (int i = 0; i < ROWS; i++) preparedInserts.add(new BenchItem("row-" + i, i));
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        try { itemsOn.dropTable().execute(conOn).join(); } catch (Exception ignored) {}
        try { itemsOff.dropTable().execute(conOff).join(); } catch (Exception ignored) {}
        if (dbCacheOn != null) dbCacheOn.close();
        if (dbCacheOff != null) dbCacheOff.close();
    }

    /**
     * {@code ROWS} single-row inserts, same SQL text every time — repeated {@code prepareStatement}
     * for the same statement is exactly what {@code cachePrepStmts} is meant to short-circuit.
     */
    @Benchmark
    public void psCacheOn(Blackhole bh) {
        for (BenchItem base : preparedInserts) {
            BenchItem item = new BenchItem(base.getName(), base.getValue());
            itemsOn.insert(item).execute(conOn).join();
            bh.consume(item.getId());
        }
    }

    @Benchmark
    public void psCacheOff(Blackhole bh) {
        for (BenchItem base : preparedInserts) {
            BenchItem item = new BenchItem(base.getName(), base.getValue());
            itemsOff.insert(item).execute(conOff).join();
            bh.consume(item.getId());
        }
    }
}
