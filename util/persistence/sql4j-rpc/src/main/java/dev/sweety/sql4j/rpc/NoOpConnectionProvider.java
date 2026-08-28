package dev.sweety.sql4j.rpc;

import dev.sweety.sql4j.api.connection.provider.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

/** A {@link ConnectionProvider} that never yields a local JDBC connection — RPC has no pool. */
public final class NoOpConnectionProvider implements ConnectionProvider {

    @Override
    public Connection get() throws SQLException {
        throw new SQLException("RemoteSqlConnection does not use a local JDBC pool");
    }

    @Override
    public void close() {
        // No pool to release.
    }
}
