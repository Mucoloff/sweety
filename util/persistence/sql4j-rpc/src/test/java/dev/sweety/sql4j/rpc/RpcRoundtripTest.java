package dev.sweety.sql4j.rpc;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.impl.Database;
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
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process roundtrip: RpcPreparedStatement → codec → SqlGatewayHandler → real local sqlite
 * {@link Database} → codec → SyntheticResultSet. No netty transport; the dispatcher calls the
 * gateway directly to prove the codec + gateway + synthetic-result chain against a real DB.
 */
class RpcRoundtripTest {

    private Path dbFile;
    private Database database;
    private SqlGatewayHandler gateway;
    private RpcPreparedStatement.RpcDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("sql4j-rpc-test", ".db");
        database = SQL4J.connect().sqlite(dbFile.toString()).open();
        gateway = new SqlGatewayHandler(database);
        dispatcher = this::dispatch;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) database.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    /** Fake dispatcher: routes RPC directly through the gateway, in-process. */
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

    @Test
    void fullRoundtripThroughGateway() throws SQLException {
        // ── CREATE TABLE (mutation, no generated keys) ────────────────────────
        RpcPreparedStatement create = new RpcPreparedStatement(
                "CREATE TABLE t (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, num INTEGER, data BLOB, extra TEXT)",
                false, dispatcher);
        assertFalse(create.execute(), "DDL execute() classifies as non-select");

        // ── INSERT returns a generated key ────────────────────────────────────
        byte[] blob = {1, 2, 3, 4, 5};
        RpcPreparedStatement insert = new RpcPreparedStatement(
                "INSERT INTO t (name, num, data, extra) VALUES (?, ?, ?, ?)", true, dispatcher);
        insert.setString(1, "hello");
        insert.setInt(2, 42);
        insert.setBytes(3, blob);
        insert.setNull(4, Types.VARCHAR);
        int affected = insert.executeUpdate();
        assertEquals(1, affected, "one row inserted");

        ResultSet keys = insert.getGeneratedKeys();
        assertTrue(keys.next(), "generated key present");
        long generatedKey = keys.getLong(1);
        assertEquals(1L, generatedKey, "first autoincrement id is 1");
        // gen-key cell survives Number.intValue() as well (boxed Long)
        ResultSet keys2 = insert.getGeneratedKeys();
        assertTrue(keys2.next());
        assertEquals(1, keys2.getInt(1));

        // ── SELECT returns the inserted row columns ───────────────────────────
        RpcPreparedStatement select = new RpcPreparedStatement(
                "SELECT id, name, num, data, extra FROM t WHERE id = ?", false, dispatcher);
        select.setInt(1, (int) generatedKey);
        ResultSet rs = select.executeQuery();
        assertTrue(rs.next(), "row found");
        assertEquals(1, rs.getInt(1), "id column");
        assertEquals("hello", rs.getString(2), "String column roundtrips");
        assertEquals(42, rs.getInt(3), "int column roundtrips");
        Object dataCell = rs.getObject(4);
        assertInstanceOf(byte[].class, dataCell, "byte[] column roundtrips");
        assertArrayEquals(blob, (byte[]) dataCell, "byte[] contents intact");
        Object extra = rs.getObject(5);
        assertNull(extra, "null column roundtrips");
        assertTrue(rs.wasNull(), "wasNull tracks the null read");

        // ── Real column names carried end-to-end (regression guard) ───────────
        java.sql.ResultSetMetaData md = rs.getMetaData();
        assertEquals(5, md.getColumnCount(), "metadata reports 5 columns");
        assertEquals("id", md.getColumnLabel(1), "real column label, not colN");
        assertEquals("name", md.getColumnName(2), "real column name, not colN");
        assertEquals("extra", md.getColumnLabel(5));

        // ── Cells addressable BY COLUMN NAME (findColumn + getObject(label)) ───
        assertEquals(2, rs.findColumn("name"), "findColumn is 1-based");
        assertEquals(2, rs.findColumn("NAME"), "findColumn is case-insensitive");
        assertEquals(1, rs.getInt("id"), "getInt(label) binds by name");
        assertEquals("hello", rs.getString("name"), "getString(label) binds by name");
        assertEquals(42, ((Number) rs.getObject("num")).intValue(), "getObject(label) binds by name");
        assertArrayEquals(blob, (byte[]) rs.getObject("data"), "byte[] by name");
        assertNull(rs.getObject("extra"), "null column by name");

        assertFalse(rs.next(), "exactly one row");
    }

    @Test
    void codecRoundtripsTimestampNullAndBytes() {
        Timestamp ts = new Timestamp(1_725_000_000_000L);
        byte[] bytes = {9, 8, 7};
        Object[][] rows = {{ts, null, bytes, "s", 7, 11L}};
        String[] names = {"ts", "nil", "bin", "str", "i", "l"};
        byte[] encoded = RpcCodec.encodeRows(names, rows);
        RpcCodec.RpcRows result = RpcCodec.decodeRows(encoded);
        Object[][] decoded = result.rows();

        assertArrayEquals(names, result.columns(), "column names roundtrip");
        assertEquals(1, decoded.length);
        assertEquals(ts, decoded[0][0], "Timestamp cell roundtrips");
        assertNull(decoded[0][1], "null cell roundtrips");
        assertArrayEquals(bytes, (byte[]) decoded[0][2], "byte[] cell roundtrips");
        assertEquals("s", decoded[0][3], "String cell roundtrips");
        assertEquals(7, decoded[0][4], "int cell roundtrips");
        assertEquals(11L, decoded[0][5], "long cell roundtrips");
    }
}
