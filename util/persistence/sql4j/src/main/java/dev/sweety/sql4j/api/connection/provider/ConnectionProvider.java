package dev.sweety.sql4j.api.connection.provider;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionProvider extends AutoCloseable {

    Connection get() throws SQLException;

    @Override
    void close();
}

