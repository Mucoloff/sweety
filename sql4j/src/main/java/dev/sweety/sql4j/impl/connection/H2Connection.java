package dev.sweety.sql4j.impl.connection;

import dev.sweety.sql4j.api.connection.SqlConnection;

public class H2Connection extends SqlConnection {

    public H2Connection(final String database) {
        super(database, null, null);
    }

    @Override
    public String url() {
        return ""; //todo implement
    }
}
