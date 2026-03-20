package dev.sweety.sql4j;

import dev.sweety.sql4j.api.configuration.*;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.configuration.*;

import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        // SQLite with virtual threads (assuming Java 21+)
        var sqliteConfig = new SQLiteConfig("data.db");
        // Fallback to cached thread pool if virtual threads not available or just demonstrate usage
        try (var sqliteDb = new Database(sqliteConfig, Executors.newCachedThreadPool())) { 
             // Ideally: Executors.newVirtualThreadPerTaskExecutor()
            System.out.println("SQLite DB initialized with " + sqliteDb.dialect().getClass().getSimpleName());
        }

        // H2 in-memory
        var h2Config = new H2Config("mem:test", "sa", "");
        try (var h2Db = new Database(h2Config, Executors.newCachedThreadPool())) {
            System.out.println("H2 DB initialized with " + h2Db.dialect().getClass().getSimpleName());
        }
    }
}


