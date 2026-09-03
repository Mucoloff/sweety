package dev.sweety.config.binary;

import dev.sweety.config.common.ConfigurationSection;
import dev.sweety.config.common.MapConfigurationSection;
import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.config.common.serialization.ConfigSink;
import dev.sweety.config.common.serialization.ConfigSource;
import dev.sweety.config.json.JsonConfiguration;
import dev.sweety.config.yml.YamlConfiguration;
import dev.sweety.data.buffer.NioBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class UnifiedSerializationInterchangeabilityTest {

    public static class PlayerState implements ConfigSerializable {
        public String username;
        public int level;
        public double balance;
        public boolean active;
        public UUID accountId;

        public PlayerState() {}

        public PlayerState(String username, int level, double balance, boolean active, UUID accountId) {
            this.username = username;
            this.level = level;
            this.balance = balance;
            this.active = active;
            this.accountId = accountId;
        }

        @Override
        public void serialize(ConfigurationSection section) {
            section.set("username", username);
            section.set("level", level);
            section.set("balance", balance);
            section.set("active", active);
            section.set("accountId", accountId.toString());
        }

        public static PlayerState deserialize(ConfigurationSection section) {
            return new PlayerState(
                    section.getString("username"),
                    section.getInt("level"),
                    section.getDouble("balance"),
                    section.getBoolean("active"),
                    UUID.fromString(section.getString("accountId"))
            );
        }
    }

    @Test
    public void testDirectNioBufferStructuredSinkSerialization() {
        UUID uid = UUID.randomUUID();
        PlayerState player = new PlayerState("Alex", 55, 1250.75, true, uid);

        // 1. Serialize directly into NioBuffer via StructuredSink
        NioBuffer buffer = player.toBuffer();
        Assertions.assertTrue(buffer.readableBytes() > 0);

        // 2. Read back positional binary values directly
        buffer.readerIndex(0);
        Assertions.assertEquals(uid.toString(), buffer.readString()); // accountId
        Assertions.assertTrue(buffer.readBoolean());                  // active
        Assertions.assertEquals(1250.75, buffer.readDouble(), 0.001); // balance
        Assertions.assertEquals(55, buffer.readInt());                // level
        Assertions.assertEquals("Alex", buffer.readString());         // username

        buffer.release();
    }

    @Test
    public void testConfigurationSectionToFromNioBuffer() {
        ConfigurationSection section = new MapConfigurationSection();
        section.set("cluster.name", "production-eu-1");
        section.set("cluster.nodes", 16);
        section.set("cluster.secure", true);

        NioBuffer buf = NioBuffer.heap();
        section.writeToBuffer(buf);

        ConfigurationSection restored = ConfigurationSection.fromBuffer(buf);
        Assertions.assertEquals("production-eu-1", restored.getString("cluster.name"));
        Assertions.assertEquals(16, restored.getInt("cluster.nodes"));
        Assertions.assertTrue(restored.getBoolean("cluster.secure"));

        buf.release();
    }

    @Test
    public void testCrossFormatFidelity(@TempDir Path tempDir) throws IOException {
        UUID uid = UUID.randomUUID();
        PlayerState player = new PlayerState("Steve", 100, 9999.99, true, uid);

        // Test in-memory ConfigSink -> ConfigSource
        ConfigSink sink = new ConfigSink();
        player.write(sink);
        Map<String, Object> map = sink.toMap();

        ConfigSource source = new ConfigSource(map);
        PlayerState fromMap = PlayerState.deserialize(new MapConfigurationSection(map));
        Assertions.assertEquals(player.username, fromMap.username);
        Assertions.assertEquals(player.level, fromMap.level);
        Assertions.assertEquals(player.balance, fromMap.balance, 0.001);
        Assertions.assertEquals(player.accountId, fromMap.accountId);

        // Test BinaryConfiguration
        Path binFile = tempDir.resolve("player.bin");
        BinaryConfiguration binConfig = new BinaryConfiguration();
        binConfig.set("player", map);
        binConfig.save(binFile);

        BinaryConfiguration loadedBin = new BinaryConfiguration();
        loadedBin.load(binFile);
        Assertions.assertEquals("Steve", loadedBin.getString("player.username"));
        Assertions.assertEquals(100, loadedBin.getInt("player.level"));

        // Test YamlConfiguration
        Path yamlFile = tempDir.resolve("player.yml");
        YamlConfiguration yamlConfig = new YamlConfiguration();
        yamlConfig.set("player", map);
        yamlConfig.save(yamlFile);

        YamlConfiguration loadedYaml = new YamlConfiguration();
        loadedYaml.load(yamlFile);
        Assertions.assertEquals("Steve", loadedYaml.getString("player.username"));

        // Test JsonConfiguration
        Path jsonFile = tempDir.resolve("player.json");
        JsonConfiguration jsonConfig = new JsonConfiguration();
        jsonConfig.set("player", map);
        jsonConfig.save(jsonFile);

        JsonConfiguration loadedJson = new JsonConfiguration();
        loadedJson.load(jsonFile);
        Assertions.assertEquals("Steve", loadedJson.getString("player.username"));
    }
}
