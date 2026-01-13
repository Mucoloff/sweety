package dev.sweety.sql4j.impl.connection;

import dev.sweety.sql4j.api.connection.SqlConnection;

public class SQLiteConnection extends SqlConnection {

    public SQLiteConnection(final String database) {
        super(database, null, null);
    }

    @Override
    public String url() {
        final String database = this.database();
        final String dbFile = database.endsWith(".db") ? database : database + ".db";
        return "jdbc:sqlite:" + dbFile;
    }

}
