package dev.sweety.sql4j.rpc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * A fake {@link PreparedStatement} that:
 * <ol>
 *   <li>Captures all {@code setXxx(int, value)} calls into an ordered param list.</li>
 *   <li>On {@link #executeQuery()} / {@link #executeUpdate()} / {@link #getGeneratedKeys()},
 *       delegates to a {@link RpcDispatcher} that performs the actual RPC call and returns
 *       results as binary payloads decoded into {@link SyntheticResultSet}.</li>
 * </ol>
 *
 * <p>This lets sql4j's existing entity-mapping code ({@code query.bind(ps)},
 * {@code query.execute(ps)}) work transparently against a remote database.
 */
public final class RpcPreparedStatement implements PreparedStatement {

    /** Callback for executing the captured query remotely. */
    @FunctionalInterface
    public interface RpcDispatcher {
        /**
         * @param sql              the SQL string
         * @param params           captured parameter values (in binding order)
         * @param returnGenKeys    whether to request generated keys
         * @return encoded result payload:
         *         - for SELECT: row matrix bytes (use {@link RpcCodec#decodeRows})
         *         - for mutation: mutation bytes (use {@link RpcCodec#decodeMutation})
         */
        RpcResponse dispatch(String sql, Object[] params, boolean returnGenKeys) throws SQLException;
    }

    public record RpcResponse(boolean isSelect, byte[] payload) {}

    private final String sql;
    private final boolean returnGeneratedKeys;
    private final RpcDispatcher dispatcher;

    private final List<Object> params = new ArrayList<>();
    private int maxParamIdx = 0;

    // Cached results after dispatch
    private Object[][] queryRows;
    private long[] mutationResult;

    public RpcPreparedStatement(String sql, boolean returnGeneratedKeys, RpcDispatcher dispatcher) {
        this.sql = sql;
        this.returnGeneratedKeys = returnGeneratedKeys;
        this.dispatcher = dispatcher;
    }

    // ─── Param capture ───────────────────────────────────────────────────────

    private void set(int idx, Object value) {
        while (params.size() < idx) params.add(null);
        params.set(idx - 1, value);
        if (idx > maxParamIdx) maxParamIdx = idx;
    }

    @Override public void setNull(int idx, int sqlType)                      { set(idx, null); }
    @Override public void setNull(int idx, int sqlType, String typeName)     { set(idx, null); }
    @Override public void setBoolean(int idx, boolean x)                     { set(idx, x); }
    @Override public void setByte(int idx, byte x)                           { set(idx, x); }
    @Override public void setShort(int idx, short x)                         { set(idx, x); }
    @Override public void setInt(int idx, int x)                             { set(idx, x); }
    @Override public void setLong(int idx, long x)                           { set(idx, x); }
    @Override public void setFloat(int idx, float x)                         { set(idx, x); }
    @Override public void setDouble(int idx, double x)                       { set(idx, x); }
    @Override public void setBigDecimal(int idx, BigDecimal x)               { set(idx, x); }
    @Override public void setString(int idx, String x)                       { set(idx, x); }
    @Override public void setBytes(int idx, byte[] x)                        { set(idx, x); }
    @Override public void setDate(int idx, Date x)                           { set(idx, x); }
    @Override public void setDate(int idx, Date x, Calendar cal)             { set(idx, x); }
    @Override public void setTime(int idx, Time x)                           { set(idx, x); }
    @Override public void setTime(int idx, Time x, Calendar cal)             { set(idx, x); }
    @Override public void setTimestamp(int idx, Timestamp x)                 { set(idx, x); }
    @Override public void setTimestamp(int idx, Timestamp x, Calendar cal)   { set(idx, x); }
    @Override public void setObject(int idx, Object x, int targetSqlType)    { set(idx, x); }
    @Override public void setObject(int idx, Object x, int t, int scale)     { set(idx, x); }
    @Override public void setObject(int idx, Object x)                       { set(idx, x); }
    @Override public void setURL(int idx, URL x)                             { set(idx, x); }
    @Override public void setAsciiStream(int i, InputStream s, int l)        { throw new UnsupportedOperationException(); }
    @Override public void setAsciiStream(int i, InputStream s, long l)       { throw new UnsupportedOperationException(); }
    @Override public void setAsciiStream(int i, InputStream s)               { throw new UnsupportedOperationException(); }
    @Override public void setUnicodeStream(int i, InputStream s, int l)      { throw new UnsupportedOperationException(); }
    @Override public void setBinaryStream(int i, InputStream s, int l)       { throw new UnsupportedOperationException(); }
    @Override public void setBinaryStream(int i, InputStream s, long l)      { throw new UnsupportedOperationException(); }
    @Override public void setBinaryStream(int i, InputStream s)              { throw new UnsupportedOperationException(); }
    @Override public void setCharacterStream(int i, Reader r, int l)         { throw new UnsupportedOperationException(); }
    @Override public void setCharacterStream(int i, Reader r, long l)        { throw new UnsupportedOperationException(); }
    @Override public void setCharacterStream(int i, Reader r)                { throw new UnsupportedOperationException(); }
    @Override public void setRef(int i, Ref x)                               { throw new UnsupportedOperationException(); }
    @Override public void setBlob(int i, Blob x)                             { throw new UnsupportedOperationException(); }
    @Override public void setBlob(int i, InputStream s, long l)              { throw new UnsupportedOperationException(); }
    @Override public void setBlob(int i, InputStream s)                      { throw new UnsupportedOperationException(); }
    @Override public void setClob(int i, Clob x)                             { throw new UnsupportedOperationException(); }
    @Override public void setClob(int i, Reader r, long l)                   { throw new UnsupportedOperationException(); }
    @Override public void setClob(int i, Reader r)                           { throw new UnsupportedOperationException(); }
    @Override public void setArray(int i, Array x)                           { throw new UnsupportedOperationException(); }
    @Override public void setNString(int i, String v)                        { set(i, v); }
    @Override public void setNCharacterStream(int i, Reader r, long l)       { throw new UnsupportedOperationException(); }
    @Override public void setNCharacterStream(int i, Reader r)               { throw new UnsupportedOperationException(); }
    @Override public void setNClob(int i, NClob x)                          { throw new UnsupportedOperationException(); }
    @Override public void setNClob(int i, Reader r, long l)                  { throw new UnsupportedOperationException(); }
    @Override public void setNClob(int i, Reader r)                          { throw new UnsupportedOperationException(); }
    @Override public void setSQLXML(int i, SQLXML x)                         { throw new UnsupportedOperationException(); }
    @Override public void setRowId(int i, RowId x)                           { throw new UnsupportedOperationException(); }

    // ─── Execution ───────────────────────────────────────────────────────────

    @Override
    public ResultSet executeQuery() throws SQLException {
        Object[] paramArray = params.toArray();
        RpcResponse resp = dispatcher.dispatch(sql, paramArray, false);
        queryRows = RpcCodec.decodeRows(resp.payload());
        return new SyntheticResultSet(queryRows);
    }

    @Override
    public int executeUpdate() throws SQLException {
        Object[] paramArray = params.toArray();
        RpcResponse resp = dispatcher.dispatch(sql, paramArray, returnGeneratedKeys);
        mutationResult = RpcCodec.decodeMutation(resp.payload());
        return (int) mutationResult[0];
    }

    @Override
    public boolean execute() throws SQLException {
        executeUpdate();
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        if (mutationResult == null) {
            return new SyntheticResultSet(new Object[0][]);
        }
        long key = mutationResult[1];
        return SyntheticResultSet.singleValue(key);
    }

    // ─── Trivial / unsupported ────────────────────────────────────────────────

    @Override public void clearParameters() { params.clear(); }
    @Override public void addBatch() {}
    @Override public int[] executeBatch() { throw new UnsupportedOperationException(); }
    @Override public void close() {}
    @Override public boolean isClosed() { return false; }
    @Override public int getMaxFieldSize() { return 0; }
    @Override public void setMaxFieldSize(int max) {}
    @Override public int getMaxRows() { return 0; }
    @Override public void setMaxRows(int max) {}
    @Override public void setEscapeProcessing(boolean enable) {}
    @Override public int getQueryTimeout() { return 0; }
    @Override public void setQueryTimeout(int seconds) {}
    @Override public void cancel() {}
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public void setCursorName(String name) {}
    @Override public ResultSet getResultSet() { return null; }
    @Override public int getUpdateCount() { return mutationResult != null ? (int) mutationResult[0] : -1; }
    @Override public boolean getMoreResults() { return false; }
    @Override public void setFetchDirection(int dir) {}
    @Override public int getFetchDirection() { return ResultSet.FETCH_FORWARD; }
    @Override public void setFetchSize(int rows) {}
    @Override public int getFetchSize() { return 0; }
    @Override public int getResultSetConcurrency() { return ResultSet.CONCUR_READ_ONLY; }
    @Override public int getResultSetType() { return ResultSet.TYPE_FORWARD_ONLY; }
    @Override public void addBatch(String sql) {}
    @Override public void clearBatch() {}
    @Override public Connection getConnection() { return null; }
    @Override public boolean getMoreResults(int current) { return false; }
    @Override public int getResultSetHoldability() { return ResultSet.HOLD_CURSORS_OVER_COMMIT; }
    @Override public void setPoolable(boolean poolable) {}
    @Override public boolean isPoolable() { return false; }
    @Override public void closeOnCompletion() {}
    @Override public boolean isCloseOnCompletion() { return false; }
    @Override public ResultSetMetaData getMetaData() { return null; }
    @Override public ParameterMetaData getParameterMetaData() { return null; }
    @Override public ResultSet executeQuery(String sql) { throw new UnsupportedOperationException(); }
    @Override public int executeUpdate(String sql) { throw new UnsupportedOperationException(); }
    @Override public boolean execute(String sql) { throw new UnsupportedOperationException(); }
    @Override public int executeUpdate(String sql, int autoGen) { throw new UnsupportedOperationException(); }
    @Override public int executeUpdate(String sql, int[] cols) { throw new UnsupportedOperationException(); }
    @Override public int executeUpdate(String sql, String[] cols) { throw new UnsupportedOperationException(); }
    @Override public boolean execute(String sql, int autoGen) { throw new UnsupportedOperationException(); }
    @Override public boolean execute(String sql, int[] cols) { throw new UnsupportedOperationException(); }
    @Override public boolean execute(String sql, String[] cols) { throw new UnsupportedOperationException(); }
    @Override public long executeLargeUpdate() throws SQLException { return executeUpdate(); }
    @Override public long getLargeUpdateCount() { return mutationResult != null ? mutationResult[0] : -1L; }
    @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }
}
