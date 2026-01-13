package dev.sweety.sql4j.impl.connection.mysql.maria;

import dev.sweety.sql4j.impl.DialectType;
import dev.sweety.sql4j.impl.connection.mysql.MySQLConnection;

public class MariaDBConnection extends MySQLConnection {

    public MariaDBConnection(final String host, final String port, final String database, final String user, final String password, final String flags) {
        super(host, port, database, user, password, flags, DialectType.MARIADB.getDialect());
    }

    @Override
    public String url() {
        final String flags = flags();
        return "jdbc:mariadb://" + host() + ":" + port() + "/" + database() + (flags != null ? "?" + flags : "");
    }
}
