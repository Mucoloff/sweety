package dev.sweety.project;

import dev.sweety.core.system.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class YamlTest {
    public static void main(String[] args) throws IOException {

        File file = new File("config.yml");
        FileConfiguration config = new FileConfiguration();

        config.set("app.name", "YamlTestApp");
        config.set("app.version", 1);
        config.set("app.active", true);

        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("key1", "value1");
        nestedMap.put("key2", 42);
        config.set("app.nested", nestedMap);
        config.save(file);

        OperatingSystem.WINDOWS.open(file);

        config.load(file);
        System.out.println("App Name: " + config.getString("app.name"));
        System.out.println("App Version: " + config.getInt("app.version"));
        System.out.println("App Active: " + config.getBoolean("app.active"));
        Map<String, Object> loadedNestedMap = config.getMap("app.nested");
        System.out.println("Nested Key1: " + loadedNestedMap.get("key1"));
        System.out.println("Nested Key2: " + loadedNestedMap.get("key2"));
    }

}
