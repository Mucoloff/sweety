package dev.sweety.sql4j.rpc;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.service.ServiceClient;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbQueryRequest;
import dev.sweety.sql4j.rpc.packet.DbQueryResponse;
import dev.sweety.thread.ThreadUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SqlConnection} backed by RPC to a remote SQL gateway instead of a local JDBC pool.
 *
 * <p>{@link #executeAsync(Query)} captures the SQL and parameters via {@link RpcPreparedStatement},
 * dispatches an RPC request to the gateway service (generic {@code dbServiceId}), receives the
 * result, and returns it through a {@link SyntheticResultSet} so the original query's entity-mapping
 * code runs unchanged.
 */
public final class RemoteSqlConnection extends SqlConnection {

    private static final Executor VIRTUAL = ThreadUtil.virtualThreadExecutor("sql4j-rpc");

    private static final long RPC_TIMEOUT_SEC = Long.parseLong(
            System.getenv().getOrDefault("SQL_RPC_TIMEOUT_SEC", "25"));

    private final ServiceClient client;
    private final int dbServiceId;

    // A method reference to dispatch() alone would only satisfy the interface's single
    // abstract method and inherit the sequential-fallback dispatchBatch() default — this
    // instance overrides both so batches actually take the single-roundtrip DbBatchMutationRequest
    // path instead of falling back to N dispatch() calls.
    private final RpcPreparedStatement.RpcDispatcher dispatcher = new RpcPreparedStatement.RpcDispatcher() {
        @Override public RpcPreparedStatement.RpcResponse dispatch(String sql, Object[] params, boolean returnGenKeys) throws SQLException {
            return RemoteSqlConnection.this.dispatch(sql, params, returnGenKeys);
        }
        @Override public int[] dispatchBatch(String sql, Object[][] paramRows) throws SQLException {
            return RemoteSqlConnection.this.dispatchBatch(sql, paramRows);
        }
    };

    public RemoteSqlConnection(ServiceClient client, int dbServiceId, DialectType dialect) {
        super(dialect, new NoOpConnectionProvider(), VIRTUAL, false);
        this.client = client;
        this.dbServiceId = dbServiceId;
    }

    @Override
    public <T> CompletableFuture<T> executeAsync(Query<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            RpcPreparedStatement ps = new RpcPreparedStatement(
                    query.sql(), query.returnGeneratedKeys(), dispatcher);
            try {
                query.bind(ps);
                return query.execute(ps);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, VIRTUAL);
    }

    private RpcPreparedStatement.RpcResponse dispatch(String sql, Object[] params, boolean returnGenKeys)
            throws SQLException {
        boolean select = isSelect(sql);
        Packet request = select
                ? new DbQueryRequest(sql, params, returnGenKeys)
                : new DbMutationRequest(sql, params, returnGenKeys);
        try {
            Packet reply = client.sendRequest(dbServiceId, request).get(RPC_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (select) {
                DbQueryResponse resp = (DbQueryResponse) reply;
                if (resp.error() != null) throw new SQLException("RPC query failed: " + resp.error());
                return RpcPreparedStatement.RpcResponse.ofRows(resp.columns(), resp.rows());
            } else {
                DbMutationResponse resp = (DbMutationResponse) reply;
                if (resp.error() != null) throw new SQLException("RPC mutation failed: " + resp.error());
                return RpcPreparedStatement.RpcResponse.ofMutation(resp.updateCount(), resp.generatedKey());
            }
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("RPC transport error: " + e.getMessage(), e);
        }
    }

    private int[] dispatchBatch(String sql, Object[][] paramRows) throws SQLException {
        try {
            Packet reply = client.sendRequest(dbServiceId, new DbBatchMutationRequest(sql, paramRows))
                    .get(RPC_TIMEOUT_SEC, TimeUnit.SECONDS);
            DbBatchMutationResponse resp = (DbBatchMutationResponse) reply;
            if (resp.error() != null) throw new SQLException("RPC batch mutation failed: " + resp.error());
            return resp.updateCounts();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("RPC transport error: " + e.getMessage(), e);
        }
    }

    @Override
    public Connection connection() throws SQLException {
        throw new SQLException("RemoteSqlConnection does not use a local JDBC pool");
    }

    @Override
    public void close() {
        // No pool to release — remote connection is stateless.
    }

    private static boolean isSelect(String sql) {
        if (sql == null) return false;
        String upper = sql.strip().toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }
}
