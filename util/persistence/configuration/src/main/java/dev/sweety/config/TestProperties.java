package dev.sweety.config;

import dev.sweety.config.common.Configuration;
import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.config.yml.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TestProperties {

    protected static class TestObj implements ConfigSerializable {

        String name;
        int age;
        Map<String, Object> map = tree();

        public TestObj(String name, int age) {
            this.name = name;
            this.age = age;
            map.put("deaths", 10);
            map.put("kills", 5);
        }

        public TestObj(Map<String, Object> me) {
            this.name = getAs(me, "name");
            this.age = getAs(me, "age");
            this.map = getAs(me, "map");
        }

        @Override
        public String toString() {
            return "obj(%s, %d)".formatted(name, age);
        }

        @Override
        public Map<String, Object> serialize() {
            final Map<String, Object> me = tree();
            me.put("name", name);
            me.put("age", age);
            me.put("map", map);
            return me;
        }
    }

    public static void main(String[] args) throws IOException {
        Configuration configuration = new YamlConfiguration();

        configuration.set("test.key", "test value");

        configuration.set("test.list", Arrays.asList("a", "b", "c"));

        configuration.set("test.obj", new TestObj("test", 10));

        File file = new File("test.yml");

        configuration.save(file);


        configuration.load(file);

        testRead(configuration);
    }

    private static void testRead(Configuration configuration) {
        System.out.println(configuration.getString("test.key"));
        System.out.println(configuration.getStringList("test.list"));
        System.out.println(configuration.getSerializable("test.obj", TestObj.class));

    }


}
