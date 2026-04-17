package dev.sweety.config;

import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.config.prop.PropConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TestProperties {

    protected static class TestObj implements ConfigSerializable {

        String name;
        int age;
        Map<String, Object> map = new HashMap<>();

        public TestObj(String name, int age) {
            this.name = name;
            this.age = age;
            map.put("deaths", 10);
            map.put("kills", 5);
        }

        public TestObj(Map<String, Object> me) {
            this.name = (String) me.get("name");
            this.age = (int) me.get("age");
            this.map = (Map<String, Object>) me.get("map");
        }

        @Override
        public String toString() {
            return "obj(%s, %d)".formatted(name, age);
        }

        @Override
        public Map<String, Object> serialize() {
            final Map<String, Object> me = new HashMap<>();
            me.put("name", name);
            me.put("age", age);
            me.put("map", map);
            return me;
        }
    }

    public static void main(String[] args) throws IOException {
        PropConfiguration configuration = new PropConfiguration();

        configuration.set("test.key", "test value");

        configuration.set("test.list", Arrays.asList("a", "b", "c"));

        configuration.set("test.obj", new TestObj("test", 10));


        configuration.save(new File("test.properties"));


        configuration.load(new File("test.properties"));

        testRead(configuration);
    }

    private static void testRead(PropConfiguration configuration) {
        System.out.println(configuration.getString("test.key"));
        System.out.println(configuration.getStringList("test.list"));
        System.out.println(configuration.getSerializable("test.obj", TestObj.class));

    }


}
