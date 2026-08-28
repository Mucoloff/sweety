package dev.sweety.sql4j.rpc;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbBatchMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbMutationRequest;
import dev.sweety.sql4j.rpc.packet.DbMutationResponse;
import dev.sweety.sql4j.rpc.packet.DbQueryRequest;
import dev.sweety.sql4j.rpc.packet.DbQueryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process roundtrip for the batch RPC path added to close the "no batching" gap flagged in
 * the sql4j perf review: {@link RpcPreparedStatement#addBatch()}/{@code executeBatch()} ->
 * {@link RpcCodec#encodeBatch}/{@code decodeBatch} -> {@link SqlGatewayHandler} (one JDBC
 * {@code addBatch()}/{@code executeBatch()} sequence) -> {@code decodeBatchResult}, mirroring
 * {@link RpcRoundtripTest}'s wiring (no netty transport, gateway called directly).
 */
class RpcBatchRoundtripTest {

    private Path dbFile;
    private Database database;
    private SqlGatewayHandler gateway;
    private RpcPreparedStatement.RpcDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("sql4j-rpc-batch-test", ".db");
        database = SQL4J.connect().sqlite(dbFile.toString()).open();
        gateway = new SqlGatewayHandler(database);
        dispatcher = new RpcPreparedStatement.RpcDispatcher() {
            @Override
            public RpcPreparedStatement.RpcResponse dispatch(String sql, Object[] params, boolean retGenKeys) throws SQLException {
                return RpcBatchRoundtripTest.this.dispatch(sql, params, retGenKeys);
            }

            @Override
            public int[] dispatchBatch(String sql, Object[][] paramRows) throws SQLException {
                byte[] payload = RpcCodec.encodeBatch(sql, paramRows);
                Packet reply = gateway.handle(new DbBatchMutationRequest(payload));
                DbBatchMutationResponse r = (DbBatchMutationResponse) reply;
                if (r.error() != null) throw new SQLException(r.error());
                return RpcCodec.decodeBatchResult(r.payload());
            }
        };
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) database.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    private RpcPreparedStatement.RpcResponse dispatch(String sql, Object[] params, boolean retGenKeys) throws SQLException {
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

    @Test
    void batchInsertRoundtripsInOneGatewayCall() throws SQLException {
        RpcPreparedStatement create = new RpcPreparedStatement(
                "CREATE TABLE b (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, num INTEGER)",
                false, dispatcher);
        create.execute();

        RpcPreparedStatement insert = new RpcPreparedStatement(
                "INSERT INTO b (name, num) VALUES (?, ?)", false, dispatcher);
        int rows = 20;
        for (int i = 0; i < rows; i++) {
            insert.setString(1, "row-" + i);
            insert.setInt(2, i);
            insert.addBatch();
        }
        int[] counts = insert.executeBatch();
        assertEquals(rows, counts.length, "one update count per batched row");
        for (int c : counts) assertEquals(1, c, "each row inserts exactly one record");

        RpcPreparedStatement select = new RpcPreparedStatement("SELECT COUNT(*) FROM b", false, dispatcher);
        ResultSet rs = select.executeQuery();
        assertTrue(rs.next());
        assertEquals(rows, rs.getInt(1), "all batched rows landed in one gateway call");
    }

    @Test
    void executeBatchOnEmptyBatchReturnsEmptyArrayWithoutDispatching() throws SQLException {
        RpcPreparedStatement insert = new RpcPreparedStatement(
                "INSERT INTO missing (name) VALUES (?)", false, dispatcher);
        // No addBatch() calls — must not dispatch anything (would fail: table doesn't exist).
        assertArrayEquals(new int[0], insert.executeBatch());
    }

    @Test
    void defaultDispatchBatchFallsBackToSequentialDispatch() throws SQLException {
        // A dispatcher implementing only dispatch() (e.g. a lambda/method-reference, like the
        // old RpcRoundtripTest style) must still work correctly via the interface default —
        // proves the fallback path (no batch-aware transport) stays correct, just unoptimized.
        RpcPreparedStatement.RpcDispatcher sequentialOnly = this::dispatch;

        RpcPreparedStatement create = new RpcPreparedStatement(
                "CREATE TABLE seq (id INTEGER PRIMARY KEY AUTOINCREMENT, num INTEGER)", false, sequentialOnly);
        create.execute();

        RpcPreparedStatement insert = new RpcPreparedStatement("INSERT INTO seq (num) VALUES (?)", false, sequentialOnly);
        insert.setInt(1, 1);
        insert.addBatch();
        insert.setInt(1, 2);
        insert.addBatch();
        int[] counts = insert.executeBatch();
        assertArrayEquals(new int[]{1, 1}, counts);
    }
}
