package dev.sweety.sql4j.impl.connection;

public class MariaDBConnection extends MySQLConnection {

    public MariaDBConnection(final String host, final String port, final String database, final String user, final String password, final String flags) {
        super(host, port, database, user, password, flags);
    }

    @Override
    public String url() {
        final String flags = flags();
        return "jdbc:mariadb://" + host() + ":" + port() + "/" + database() + (flags != null ? "?" + flags : "");
    }
}
