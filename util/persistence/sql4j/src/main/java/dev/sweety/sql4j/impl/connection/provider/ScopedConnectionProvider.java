package dev.sweety.sql4j.impl.connection.provider;

import dev.sweety.sql4j.api.connection.SqlRunner;
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

    private ScopedConnectionProvider(Connection connection) {
        this.connection = connection;
        this.wrapper = new NonClosingConnection(connection);
    }

    public static ScopedConnectionProvider of(Connection connection) {
        return new ScopedConnectionProvider(connection);
    }

    @Override
    public Connection get() throws SQLException {
        if (released) throw new SQLException("ScopedConnectionProvider already released");
        return wrapper;
    }

    public void release() {
        if (!released) {
            released = true;
            try {
                connection.close();
            } catch (SQLException e) {
                // release() must be idempotent and safe on any code path (including finally
                // blocks), so a failed close is logged rather than propagated.
                SqlRunner.getLogger().log("Failed to close scoped connection: %s", e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        release();
    }
}
