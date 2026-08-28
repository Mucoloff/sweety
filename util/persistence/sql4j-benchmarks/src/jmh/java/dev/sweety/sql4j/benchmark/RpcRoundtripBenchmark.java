package dev.sweety.sql4j.benchmark;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.rpc.RpcCodec;
import dev.sweety.sql4j.rpc.RpcPreparedStatement;
import dev.sweety.sql4j.rpc.SqlGatewayHandler;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbQueryRequest;
import dev.sweety.sql4j.rpc.packet.DbQueryResponse;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Drives {@link RpcPreparedStatement} -> {@link RpcCodec} -> {@link SqlGatewayHandler} -> a real
 * in-memory H2 {@link Database} -> codec -> {@code SyntheticResultSet}, exactly like
 * {@code RpcRoundtripTest} but as a JMH benchmark (no netty transport in the loop — this
 * isolates codec + gateway + dispatch cost from actual network latency).
 *
 * <p>{@code sequentialInserts} uses the plain {@code dispatch()}-only dispatcher (sequential
 * fallback), quantifying N blocking single-row RPC roundtrips; {@code batchInsert} uses the
 * batch-aware dispatcher wired to {@link DbBatchMutationRequest} (see
 * {@code RpcPreparedStatement.RpcDispatcher.dispatchBatch}), landing all {@code rows} in one
 * gateway roundtrip — the fix for the no-batching gap flagged in the perf review.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class RpcRoundtripBenchmark {

    @Param({"1", "50", "200"})
    int rows;

    private Database database;
    private SqlGatewayHandler gateway;
    private RpcPreparedStatement.RpcDispatcher dispatcher;
    private RpcPreparedStatement.RpcDispatcher batchDispatcher;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        String dbPath = "mem:rpc_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        database = SQL4J.connect().h2(dbPath, "sa", "").open();
        gateway = new SqlGatewayHandler(database);
        dispatcher = this::dispatch;
        batchDispatcher = new RpcPreparedStatement.RpcDispatcher() {
            @Override public RpcPreparedStatement.RpcResponse dispatch(String sql, Object[] params, boolean retGenKeys) throws SQLException {
                return RpcRoundtripBenchmark.this.dispatch(sql, params, retGenKeys);
            }
            @Override public int[] dispatchBatch(String sql, Object[][] paramRows) throws SQLException {
                byte[] payload = RpcCodec.encodeBatch(sql, paramRows);
                Packet reply = gateway.handle(new DbBatchMutationRequest(payload));
                DbBatchMutationResponse r = (DbBatchMutationResponse) reply;
                if (r.error() != null) throw new SQLException(r.error());
                return RpcCodec.decodeBatchResult(r.payload());
            }
        };

        RpcPreparedStatement create = new RpcPreparedStatement(
                "CREATE TABLE rpc_items (id INTEGER PRIMARY KEY AUTO_INCREMENT, name VARCHAR(64), num INTEGER)",
                false, dispatcher);
        create.execute();
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        database.close();
    }

    /** Truncates, then re-seeds {@code rows} rows (unmeasured setup) so {@link #selectRows}
     * always reads back exactly {@code rows} rows regardless of what {@link #sequentialInserts}
     * did in a prior iteration. */
    @Setup(Level.Iteration)
    public void reseedTable() throws Exception {
        RpcPreparedStatement truncate = new RpcPreparedStatement("DELETE FROM rpc_items", false, dispatcher);
        truncate.executeUpdate();
        for (int i = 0; i < rows; i++) {
            RpcPreparedStatement insert = new RpcPreparedStatement(
                    "INSERT INTO rpc_items (name, num) VALUES (?, ?)", true, dispatcher);
            insert.setString(1, "seed-" + i);
            insert.setInt(2, i);
            insert.executeUpdate();
        }
    }

    /**
     * {@code rows} single-row INSERT RPC roundtrips, one blocking {@code dispatch()} call
     * each — the actual current cost of "inserting {@code rows} rows over RPC" given no batch
     * path exists. Adds on top of the seeded rows; each iteration re-seeds from empty.
     */
    @Benchmark
    public void sequentialInserts(Blackhole bh) throws Exception {
        for (int i = 0; i < rows; i++) {
            RpcPreparedStatement insert = new RpcPreparedStatement(
                    "INSERT INTO rpc_items (name, num) VALUES (?, ?)", true, dispatcher);
            insert.setString(1, "item-" + i);
            insert.setInt(2, i);
            bh.consume(insert.executeUpdate());
        }
    }

    /**
     * {@code rows} rows inserted via one {@code addBatch()}/{@code executeBatch()} sequence
     * dispatched as a single {@link DbBatchMutationRequest} roundtrip — direct comparison
     * against {@link #sequentialInserts} at the same {@code rows} param.
     */
    @Benchmark
    public void batchInsert(Blackhole bh) throws Exception {
        RpcPreparedStatement insert = new RpcPreparedStatement(
                "INSERT INTO rpc_items (name, num) VALUES (?, ?)", false, batchDispatcher);
        for (int i = 0; i < rows; i++) {
            insert.setString(1, "batch-" + i);
            insert.setInt(2, i);
            insert.addBatch();
        }
        bh.consume(insert.executeBatch());
    }

    /** One SELECT roundtrip reading back the {@code rows} seeded rows. */
    @Benchmark
    public void selectRows(Blackhole bh) throws Exception {
        RpcPreparedStatement select = new RpcPreparedStatement("SELECT id, name, num FROM rpc_items", false, dispatcher);
        ResultSet rs = select.executeQuery();
        int count = 0;
        while (rs.next()) {
            bh.consume(rs.getInt(1));
            bh.consume(rs.getString(2));
            count++;
        }
        bh.consume(count);
    }

    /** Fake dispatcher: routes the RPC directly through the gateway, in-process (no netty). */
    private RpcPreparedStatement.RpcResponse dispatch(String sql, Object[] params, boolean retGenKeys)
            throws SQLException {
        boolean select = isSelect(sql);
        byte[] payload = RpcCodec.encodeQuery(sql, params, retGenKeys);
        Packet reply = gateway.handle(select ? new DbQueryRequest(payload) : new DbMutationRequest(payload));
        if (select) {
            DbQueryResponse r = (DbQueryResponse) reply;
            if (r.error() != null) throw new SQLException(r.error());
            return new RpcPreparedStatement.RpcResponse(true, r.payload());
        }
        DbMutationResponse r = (DbMutationResponse) reply;
        if (r.error() != null) throw new SQLException(r.error());
        return new RpcPreparedStatement.RpcResponse(false, r.payload());
    }

    private static boolean isSelect(String sql) {
        String upper = sql.strip().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }
}
