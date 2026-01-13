package dev.sweety.sql4j.impl.connection;

import dev.sweety.sql4j.api.connection.SqlConnection;

public class PostgreSQLConnection extends SqlConnection {

    public PostgreSQLConnection(final String database,final  String user, final String password) {
        super(database, user, password);
    }

    @Override
    public String url() {
        return ""; //todo implement
    }
}
