package dev.sweety.sql4j.impl.connection.provider;

import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A {@link ConnectionProvider} that wraps a single borrowed JDBC connection and
 * suppresses {@code close()} on each borrow so the connection is reused across queries.
 * The actual close happens when {@link #release()} is called (or via try-with-resources
 * on the owning {@link dev.sweety.sql4j.api.connection.SqlConnection}).
 */
public final class ScopedConnectionProvider implements ConnectionProvider {

    private final Connection connection;
    private final NonClosingConnection wrapper;
    private volatile boolean released = false;

    public ScopedConnectionProvider(Connection connection) {
        this.connection = connection;
        this.wrapper = new NonClosingConnection(connection);
    }

    @Override
    public Connection get() throws SQLException {
        if (released) throw new SQLException("ScopedConnectionProvider already released");
        return wrapper;
    }

    public void release() {
        if (!released) {
            released = true;
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    @Override
    public void close() {
        release();
    }
}
