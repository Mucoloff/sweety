package dev.sweety.sql4j.impl.connection.provider;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Wraps a JDBC {@link Connection} and suppresses {@link #close()} so that
 * {@code try-with-resources} blocks inside {@code executeSync} don't return the
 * connection to the pool before the scoped block is done.
 */
final class NonClosingConnection implements Connection {

    private final Connection delegate;

    NonClosingConnection(Connection delegate) { this.delegate = delegate; }

    @Override public void close() { /* suppressed — real close via ScopedConnectionProvider.release() */ }
    @Override public boolean isClosed() throws SQLException { return delegate.isClosed(); }

    @Override public Statement createStatement() throws SQLException { return delegate.createStatement(); }
    @Override public PreparedStatement prepareStatement(String sql) throws SQLException { return delegate.prepareStatement(sql); }
    @Override public CallableStatement prepareCall(String sql) throws SQLException { return delegate.prepareCall(sql); }
    @Override public String nativeSQL(String sql) throws SQLException { return delegate.nativeSQL(sql); }
    @Override public void setAutoCommit(boolean ac) throws SQLException { delegate.setAutoCommit(ac); }
    @Override public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
    @Override public void commit() throws SQLException { delegate.commit(); }
    @Override public void rollback() throws SQLException { delegate.rollback(); }
    @Override public DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
    @Override public void setReadOnly(boolean ro) throws SQLException { delegate.setReadOnly(ro); }
    @Override public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
    @Override public void setCatalog(String c) throws SQLException { delegate.setCatalog(c); }
    @Override public String getCatalog() throws SQLException { return delegate.getCatalog(); }
    @Override public void setTransactionIsolation(int l) throws SQLException { delegate.setTransactionIsolation(l); }
    @Override public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
    @Override public SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
    @Override public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
    @Override public Statement createStatement(int rt, int rc) throws SQLException { return delegate.createStatement(rt, rc); }
    @Override public PreparedStatement prepareStatement(String sql, int rt, int rc) throws SQLException { return delegate.prepareStatement(sql, rt, rc); }
    @Override public CallableStatement prepareCall(String sql, int rt, int rc) throws SQLException { return delegate.prepareCall(sql, rt, rc); }
    @Override public Map<String, Class<?>> getTypeMap() throws SQLException { return delegate.getTypeMap(); }
    @Override public void setTypeMap(Map<String, Class<?>> m) throws SQLException { delegate.setTypeMap(m); }
    @Override public void setHoldability(int h) throws SQLException { delegate.setHoldability(h); }
    @Override public int getHoldability() throws SQLException { return delegate.getHoldability(); }
    @Override public Savepoint setSavepoint() throws SQLException { return delegate.setSavepoint(); }
    @Override public Savepoint setSavepoint(String n) throws SQLException { return delegate.setSavepoint(n); }
    @Override public void rollback(Savepoint s) throws SQLException { delegate.rollback(s); }
    @Override public void releaseSavepoint(Savepoint s) throws SQLException { delegate.releaseSavepoint(s); }
    @Override public Statement createStatement(int rt, int rc, int rh) throws SQLException { return delegate.createStatement(rt, rc, rh); }
    @Override public PreparedStatement prepareStatement(String sql, int rt, int rc, int rh) throws SQLException { return delegate.prepareStatement(sql, rt, rc, rh); }
    @Override public CallableStatement prepareCall(String sql, int rt, int rc, int rh) throws SQLException { return delegate.prepareCall(sql, rt, rc, rh); }
    @Override public PreparedStatement prepareStatement(String sql, int[] ci) throws SQLException { return delegate.prepareStatement(sql, ci); }
    @Override public PreparedStatement prepareStatement(String sql, String[] cn) throws SQLException { return delegate.prepareStatement(sql, cn); }
    @Override public PreparedStatement prepareStatement(String sql, int agk) throws SQLException { return delegate.prepareStatement(sql, agk); }
    @Override public Clob createClob() throws SQLException { return delegate.createClob(); }
    @Override public Blob createBlob() throws SQLException { return delegate.createBlob(); }
    @Override public NClob createNClob() throws SQLException { return delegate.createNClob(); }
    @Override public SQLXML createSQLXML() throws SQLException { return delegate.createSQLXML(); }
    @Override public boolean isValid(int t) throws SQLException { return delegate.isValid(t); }
    @Override public void setClientInfo(String n, String v) throws SQLClientInfoException { delegate.setClientInfo(n, v); }
    @Override public void setClientInfo(Properties p) throws SQLClientInfoException { delegate.setClientInfo(p); }
    @Override public String getClientInfo(String n) throws SQLException { return delegate.getClientInfo(n); }
    @Override public Properties getClientInfo() throws SQLException { return delegate.getClientInfo(); }
    @Override public Array createArrayOf(String t, Object[] e) throws SQLException { return delegate.createArrayOf(t, e); }
    @Override public Struct createStruct(String t, Object[] a) throws SQLException { return delegate.createStruct(t, a); }
    @Override public void setSchema(String s) throws SQLException { delegate.setSchema(s); }
    @Override public String getSchema() throws SQLException { return delegate.getSchema(); }
    @Override public void abort(Executor e) throws SQLException { delegate.abort(e); }
    @Override public void setNetworkTimeout(Executor e, int ms) throws SQLException { delegate.setNetworkTimeout(e, ms); }
    @Override public int getNetworkTimeout() throws SQLException { return delegate.getNetworkTimeout(); }
    @Override public <T> T unwrap(Class<T> i) throws SQLException { return delegate.unwrap(i); }
    @Override public boolean isWrapperFor(Class<?> i) throws SQLException { return delegate.isWrapperFor(i); }
}
