package dev.sweety.sql4j.rpc;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbQueryRequest;
import dev.sweety.sql4j.rpc.packet.DbQueryResponse;
import dev.sweety.thread.ThreadUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * Server-side helper that executes RPC SQL requests against a local {@link Database}.
 *
 * <p>Decodes a {@link DbQueryRequest}/{@link DbMutationRequest}, runs the SQL over a raw JDBC
 * connection borrowed from the {@code Database}, and returns an encoded response packet.
 *
 * <p>An {@link Semaphore} bounds concurrent in-flight tasks so a request flood can't spawn
 * unbounded work against a small connection pool (tune via {@code SQL_GW_MAX_INFLIGHT}). The
 * virtual executor is retained for callers wishing to submit asynchronously.
 */
public final class SqlGatewayHandler {

    private static final int MAX_INFLIGHT = Integer.parseInt(
            System.getenv().getOrDefault("SQL_GW_MAX_INFLIGHT", "200"));

    private final Database database;
    private final Semaphore inflight = new Semaphore(MAX_INFLIGHT);
    private final Executor virtual = ThreadUtil.virtualThreadExecutor("sql-gateway");

    public SqlGatewayHandler(Database database) {
        this.database = database;
    }

    public Executor executor() {
        return virtual;
    }

    /** Handles a request packet synchronously, returning the response packet (never null). */
    public Packet handle(Packet request) {
        if (!inflight.tryAcquire()) {
            String msg = "OVERLOAD: gateway saturated, retry later";
            if (request instanceof DbQueryRequest) return DbQueryResponse.error(msg);
            if (request instanceof DbBatchMutationRequest) return DbBatchMutationResponse.error(msg);
            return DbMutationResponse.error(msg);
        }
        try {
            return switch (request) {
                case DbQueryRequest q -> handleQuery(q);
                case DbMutationRequest m -> handleMutation(m);
                case DbBatchMutationRequest b -> handleBatchMutation(b);
                default -> throw new IllegalArgumentException(
                        "Unsupported gateway request: " + request.getClass().getName());
            };
        } finally {
            inflight.release();
        }
    }

    private Packet handleQuery(DbQueryRequest request) {
        try {
            RpcCodec.RpcQueryPayload q = RpcCodec.decodeQuery(request.payload());
            RpcCodec.RpcRows result = executeSelect(q.sql(), q.params());
            return new DbQueryResponse(RpcCodec.encodeRows(result.columns(), result.rows()));
        } catch (Exception e) {
            return DbQueryResponse.error(e.getMessage());
        }
    }

    private Packet handleMutation(DbMutationRequest request) {
        try {
            RpcCodec.RpcQueryPayload q = RpcCodec.decodeQuery(request.payload());
            long[] result = executeMutation(q.sql(), q.params(), q.returnGeneratedKeys());
            return new DbMutationResponse(RpcCodec.encodeMutation((int) result[0], result[1]));
        } catch (Exception e) {
            return DbMutationResponse.error(e.getMessage());
        }
    }

    /**
     * One JDBC {@code addBatch()}/{@code executeBatch()} sequence on one borrowed connection —
     * the batch-RPC counterpart of {@link #handleMutation}, avoiding N separate
     * {@link DbMutationRequest} roundtrips for what was previously an unsupported
     * {@code RpcPreparedStatement.executeBatch()} (see {@code RpcPreparedStatement.addBatch}).
     * No generated-keys support, matching {@link PreparedStatement#executeBatch()}'s own
     * contract (generated keys are not portably available per-row across drivers for batches).
     */
    private Packet handleBatchMutation(DbBatchMutationRequest request) {
        try {
            RpcCodec.RpcBatchPayload b = RpcCodec.decodeBatch(request.payload());
            int[] counts = executeBatch(b.sql(), b.paramRows());
            return new DbBatchMutationResponse(RpcCodec.encodeBatchResult(counts));
        } catch (Exception e) {
            return DbBatchMutationResponse.error(e.getMessage());
        }
    }

    private int[] executeBatch(String sql, Object[][] paramRows) throws SQLException {
        try (Connection conn = database.getConnection().connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] row : paramRows) {
                bindParams(ps, row);
                ps.addBatch();
            }
            return ps.executeBatch();
        }
    }

    private RpcCodec.RpcRows executeSelect(String sql, Object[] params) throws SQLException {
        try (Connection conn = database.getConnection().connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                // Carry the REAL column names end-to-end so label-based entity mappers
                // (Row.fromResultSet → getColumnLabel) can bind columns to fields over RPC.
                String[] columns = new String[colCount];
                for (int i = 0; i < colCount; i++) {
                    String label = meta.getColumnLabel(i + 1);
                    if (label == null || label.isEmpty()) label = meta.getColumnName(i + 1);
                    columns[i] = label;
                }
                ArrayList<Object[]> rows = new ArrayList<>();
                while (rs.next()) {
                    Object[] row = new Object[colCount];
                    for (int i = 0; i < colCount; i++) {
                        row[i] = rs.getObject(i + 1);
                    }
                    rows.add(row);
                }
                return new RpcCodec.RpcRows(columns, rows.toArray(new Object[0][]));
            }
        }
    }

    private long[] executeMutation(String sql, Object[] params, boolean returnGenKeys) throws SQLException {
        int flag = returnGenKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS;
        try (Connection conn = database.getConnection().connection();
             PreparedStatement ps = conn.prepareStatement(sql, flag)) {
            bindParams(ps, params);
            int updateCount = ps.executeUpdate();
            long generatedKey = -1;
            if (returnGenKeys) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) generatedKey = rs.getLong(1);
                }
            }
            return new long[]{updateCount, generatedKey};
        }
    }

    private static void bindParams(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
