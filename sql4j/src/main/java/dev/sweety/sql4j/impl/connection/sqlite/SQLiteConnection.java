package dev.sweety.sql4j.impl.connection.sqlite;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.DialectType;

public class SQLiteConnection extends SqlConnection {

    public SQLiteConnection(final String database) {
        super(database, null, null, DialectType.SQLITE);
    }

    @Override
    public String url() {
        final String database = this.database();
        final String dbFile = database.endsWith(".db") ? database : database + ".db";
        return "jdbc:sqlite:" + dbFile;
    }

}
