package dev.sweety.config.processor;

import dev.sweety.config.common.MapConfigurationSection;
import dev.sweety.config.processor.fixture.AnticheatConfig;
import dev.sweety.config.processor.fixture.AnticheatConfigImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigCodegenTest {

    @Test
    public void testGeneratedConfigLoadAndSave() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("maxSpeed", 9.81);
        map.put("alertThreshold", 5);
        map.put("prefix", "[Sweety]");

        MapConfigurationSection section = new MapConfigurationSection(map);

        AnticheatConfigImpl config = new AnticheatConfigImpl(section);

        assertEquals(9.81, config.maxSpeed());
        assertEquals(5, config.alertThreshold());
        assertEquals("[Sweety]", config.prefix());
        assertTrue(config.enableAlerts(), "Default method fallback");

        // Mutate and save
        config.setMaxSpeed(12.5);
        java.util.Map<String, Object> saveMap = new java.util.HashMap<>();
        MapConfigurationSection saveTarget = new MapConfigurationSection(saveMap);
        config.save(saveTarget);

        assertEquals(12.5, saveTarget.getDouble("maxSpeed"));
        assertEquals(5, saveTarget.getInt("alertThreshold"));
    }
}
