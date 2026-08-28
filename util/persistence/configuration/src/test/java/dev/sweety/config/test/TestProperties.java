package dev.sweety.config.test;

import dev.sweety.config.binary.BinaryConfiguration;
import dev.sweety.config.common.Configuration;
import dev.sweety.config.common.ConfigurationSection;
import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.config.json.JsonConfiguration;
import dev.sweety.config.prop.PropConfiguration;
import dev.sweety.config.toml.TomlConfiguration;
import dev.sweety.config.xml.XmlConfiguration;
import dev.sweety.config.yml.YamlConfiguration;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TestProperties {

    protected static class TestObj implements ConfigSerializable {

        String name;
        int age;
        float salary;
        double height;
        byte birthday;
        short weight;
        char gender;
        boolean honor;

        Map<String, Object> map = new TreeMap<>();

        public TestObj() {
            this.name = "name";
            this.age = 20;
            this.salary = 100;
            this.height = 0.5;
            this.birthday = 20;
            this.weight = 10;
            this.gender = 'M';
            this.honor = true;
            map.put("deaths", 10);
            map.put("kills", 5);
        }

        public TestObj(ConfigurationSection section) {
            this.name = section.getString("name");
            this.age = section.getInt("age");
            this.salary = section.getFloat("salary");
            this.height = section.getDouble("height");
            this.birthday = section.getByte("birthday");
            this.gender = section.getChar("gender");
            this.weight = section.getShort("weight");
            this.honor = section.getBoolean("honor");
            this.map = section.getMap("map");
        }

        @Override
        public String toString() {
            return "obj(%s, %d, %f, %f, %d)".formatted(name, age, salary, height, birthday);
        }

        @Override
        public void serialize(ConfigurationSection section) {
            section.set("name", name);
            section.set("age", age);
            section.set("salary", salary);
            section.set("height", height);
            section.set("birthday", birthday);
            section.set("gender", gender);
            section.set("weight", weight);
            section.set("honor", honor);
            section.set("map", map);
        }
    }

    public static void main(String[] args) {
        var tests = List.of(
                new BinaryConfiguration(),
                new JsonConfiguration(),
                new PropConfiguration(),
                new TomlConfiguration(),
                new XmlConfiguration(),
                new YamlConfiguration()

        );

        tests.forEach(TestProperties::test);
    }

    private static void test(Configuration configuration) {
        System.out.println("testing configuration " + configuration.getClass().getSimpleName());
        try {
            testWrite(configuration);
            Path file = Path.of("test." + configuration.extension());
            configuration.save(file);
            configuration.load(file);
            testRead(configuration);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private static void testWrite(Configuration configuration) {
        try {
            configuration.set("test.key", "test value");
            configuration.set("test.list", Arrays.asList("a", "b", "c"));
            configuration.set("test.obj", new TestObj());
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private static void testRead(Configuration configuration) {
        try {
            System.out.println(configuration.getString("test.key"));
            System.out.println(configuration.getStringList("test.list"));
            System.out.println("list: " + configuration.get("test.list"));
            System.out.println(configuration.getSerializable("test.obj", TestObj.class));
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

    }


}
