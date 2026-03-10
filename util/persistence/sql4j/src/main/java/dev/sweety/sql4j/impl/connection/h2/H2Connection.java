package dev.sweety.sql4j.impl.connection.h2;

import dev.sweety.sql4j.api.connection.SqlConnection;

public class H2Connection extends SqlConnection {

    public H2Connection(final String database) {
        super(database, null, null, null);
    }

    @Override
    public String url() {
        return ""; //todo implement
    }
}
