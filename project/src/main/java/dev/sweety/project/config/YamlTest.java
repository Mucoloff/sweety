package dev.sweety.project.config;

import dev.sweety.core.system.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlTest {

    public static class TestObj implements ConfigSerializable {

        String name;

        public TestObj(String name) {
            this.name = name;
        }

        @Override
        public Map<String, Object> serialize() {
            final Map<String, Object> me = new HashMap<>();
            me.put("name", name);
            return me;
        }

        public TestObj(final Map<String, Object> me) {
            this.name = (String) me.get("name");
        }

        @Override
        public String toString() {
            return "obj(%s)".formatted(name);
        }
    }


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

        List<Map<String, Object>> listOfMaps = List.of(
                Map.of("item1", "value1", "item2", 123),
                Map.of("item3", "value3", "item4", 456)
        );
        config.set("app.listOfMaps", listOfMaps);

        config.set("app.testObj", new TestObj("TestName"));

        if (true) {
            final Map<String, TestObj> objMap = new HashMap<>();
            objMap.put("obj1", new TestObj("obj1"));
            objMap.put("obj2", new TestObj("obj2"));
            config.set("app.objMap", objMap);
        }

        if (true) {
            final List<TestObj> objList = new ArrayList<>();
            objList.add(new TestObj("obj1"));
            objList.add(new TestObj("obj2"));
            config.set("app.objList", objList);
        }

        config.save(file);

        //OperatingSystem.WINDOWS.open(file);

        config.load(file);
        System.out.println("App Name: " + config.getString("app.name"));
        System.out.println("App Version: " + config.getInt("app.version"));
        System.out.println("App Active: " + config.getBoolean("app.active"));
        Map<String, Object> loadedNestedMap = config.getMap("app.nested");
        System.out.println("Nested Key1: " + loadedNestedMap.get("key1"));
        System.out.println("Nested Key2: " + loadedNestedMap.get("key2"));
        TestObj loadedTestObj = config.getSerializable("app.testObj", TestObj.class);

        config.getMapList("app.listOfMaps").forEach(map -> {
            System.out.println("List Item:");
            map.forEach((key, value) -> System.out.println("  " + key + ": " + value));
        });


        System.out.println("TestObj: " + loadedTestObj);
        Map<String, TestObj> map = config.getSerializableMap("app.objMap", TestObj.class);
        map.forEach((k, v) -> System.out.println("  " + k + ": " + v));

        List<TestObj> list = config.getSerializableList("app.objList", TestObj.class);
        list.forEach((o) -> System.out.println("  " + o));
    }

}
