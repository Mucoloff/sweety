package dev.sweety.sql4j.document;

import dev.sweety.config.common.ConfigurationSection;
import dev.sweety.config.common.MapConfigurationSection;
import dev.sweety.sql4j.SQL4J;
import dev.sweety.sql4j.impl.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class DocumentCollectionTest {

    private Database database;

    @BeforeEach
    public void setup() {
        database = SQL4J.connect().h2("mem:doc_test;DB_CLOSE_DELAY=-1", "sa", "").open();
    }

    @AfterEach
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    public static class PlayerSettings {
        public String theme = "dark";
        public int volume = 80;
        public boolean notifications = true;
        public List<String> tags = new ArrayList<>();

        public PlayerSettings() {}

        public PlayerSettings(String theme, int volume, boolean notifications, List<String> tags) {
            this.theme = theme;
            this.volume = volume;
            this.notifications = notifications;
            this.tags = tags;
        }
    }

    @Test
    public void testYamlDocumentCollection() {
        DocumentCollection<PlayerSettings, String> collection =
                database.documentCollection("player_settings", PlayerSettings.class, DocumentFormat.YAML);

        PlayerSettings s1 = new PlayerSettings("neon", 95, true, List.of("vip", "beta"));
        collection.put("user-1", s1);

        Assertions.assertTrue(collection.exists("user-1"));
        Assertions.assertEquals(1, collection.count());

        Optional<PlayerSettings> fetched = collection.get("user-1");
        Assertions.assertTrue(fetched.isPresent());
        Assertions.assertEquals("neon", fetched.get().theme);
        Assertions.assertEquals(95, fetched.get().volume);
        Assertions.assertTrue(fetched.get().notifications);
        Assertions.assertEquals(List.of("vip", "beta"), fetched.get().tags);

        // Update document
        s1.volume = 50;
        collection.put("user-1", s1);
        Assertions.assertEquals(50, collection.get("user-1").get().volume);

        // Delete
        Assertions.assertTrue(collection.delete("user-1"));
        Assertions.assertFalse(collection.exists("user-1"));
        Assertions.assertEquals(0, collection.count());
    }

    @Test
    public void testJsonConfigurationSectionCollection(@TempDir Path tempDir) throws IOException {
        DocumentCollection<ConfigurationSection, String> configs = database.jsonDocuments("guild_configs");

        ConfigurationSection c1 = new MapConfigurationSection();
        c1.set("name", "DragonSlayers");
        c1.set("level", 42);
        c1.set("open", true);

        configs.put("guild-100", c1);

        Optional<ConfigurationSection> loaded = configs.get("guild-100");
        Assertions.assertTrue(loaded.isPresent());
        Assertions.assertEquals("DragonSlayers", loaded.get().getString("name"));
        Assertions.assertEquals(42, loaded.get().getInt("level"));
        Assertions.assertTrue(loaded.get().getBoolean("open"));

        // Test Export and Import
        Path exportPath = tempDir.resolve("guilds_backup.json");
        configs.exportToFile(exportPath);
        Assertions.assertTrue(exportPath.toFile().exists());

        configs.clear();
        Assertions.assertEquals(0, configs.count());

        configs.importFromFile(exportPath);
        Assertions.assertEquals(1, configs.count());
        Assertions.assertEquals("DragonSlayers", configs.get("guild-100").get().getString("name"));
    }

    @Test
    public void testComputeIfAbsentAndFilters() {
        DocumentCollection<PlayerSettings, UUID> collection =
                database.documentCollection("uuid_profiles", PlayerSettings.class, DocumentFormat.YAML, UUID.class);

        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        collection.computeIfAbsent(u1, id -> new PlayerSettings("light", 10, false, List.of()));
        collection.computeIfAbsent(u2, id -> new PlayerSettings("dark", 90, true, List.of("admin")));

        Assertions.assertEquals(2, collection.count());

        List<PlayerSettings> loudUsers = collection.find(p -> p.volume > 50);
        Assertions.assertEquals(1, loudUsers.size());
        Assertions.assertEquals("dark", loudUsers.get(0).theme);
    }

    @Test
    public void testSqliteDocumentCollection(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("sqlite_doc.db");
        try (Database sqliteDb = SQL4J.connect().sqlite(dbPath.toString()).open()) {
            DocumentCollection<PlayerSettings, String> collection =
                    sqliteDb.documentCollection("sqlite_players", PlayerSettings.class, DocumentFormat.YAML);

            PlayerSettings ps = new PlayerSettings("cyberpunk", 100, true, List.of("mod"));
            collection.put("p-1", ps);

            Assertions.assertTrue(collection.exists("p-1"));
            Assertions.assertEquals("cyberpunk", collection.get("p-1").get().theme);

            // Test upsert on conflict
            ps.theme = "synthwave";
            collection.put("p-1", ps);
            Assertions.assertEquals("synthwave", collection.get("p-1").get().theme);
        }
    }
}
