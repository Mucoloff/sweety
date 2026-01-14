package dev.sweety.sql4j.impl.connection.mysql;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.DialectType;

public class MySQLConnection extends SqlConnection {
    private final String host;
    private final String port;
    private final String flags;

    public MySQLConnection(final String host, final String port, final String database, final String user, final String password, final String flags) {
        this(database, user, password,host,port,flags, DialectType.MYSQL);
    }

    public MySQLConnection(final String host, final String port, final String database, final String user, final String password, final String flags, final DialectType dialect) {
        super(database, user, password, dialect);
        this.host = host;
        this.port = port;
        this.flags = flags;
    }

    @Override
    public String url() {
        final String flags = flags();
        return "jdbc:mysql://" + host() + ":" + port() + "/" + database() + (flags != null ? "?" + flags : "");
    }

    public String host() {
        return host;
    }

    public String port() {
        return port;
    }

    public String flags() {
        return flags;
    }
}
