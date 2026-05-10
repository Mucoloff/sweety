package dev.sweety.sql4j;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.configuration.MySQLConfig;
import dev.sweety.sql4j.impl.configuration.SQLiteConfig;
import dev.sweety.sql4j.impl.configuration.PostgreSQLConfig;
import dev.sweety.sql4j.impl.configuration.MariaDBConfig;
import dev.sweety.sql4j.impl.configuration.H2Config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SQL4J {

    public static DatabaseBuilder connect() {
        return new DatabaseBuilder();
    }

    public static class DatabaseBuilder {
        private DatabaseConfig config;
        private Executor executor = Executors.newCachedThreadPool();
        private boolean useCache = true;

        public DatabaseBuilder sqlite(String path) {
            this.config = new SQLiteConfig(path);
            return this;
        }

        public DatabaseBuilder mysql(String host, int port, String database, String user, String password) {
            this.config = new MySQLConfig(host, port, database, user, password);
            return this;
        }
        
        public DatabaseBuilder postgres(String host, int port, String database, String user, String password) {
            this.config = new PostgreSQLConfig(host, port, database, user, password);
            return this;
        }

        public DatabaseBuilder mariadb(String host, int port, String database, String user, String password) {
            this.config = new MariaDBConfig(host, port, database, user, password);
            return this;
        }

        public DatabaseBuilder h2(String path) {
            this.config = new H2Config(path);
            return this;
        }

        public DatabaseBuilder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public DatabaseBuilder withCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public Database open() {
            if (config == null) throw new IllegalStateException("Database configuration not set");
            Database db = new Database(config, executor);
            db.entityCache().setEnabled(useCache);
            return db;
        }
    }
}
