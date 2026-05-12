package dev.sweety.config;

import dev.sweety.config.binary.BinaryConfiguration;
import dev.sweety.config.common.Configuration;
import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.config.json.JsonConfiguration;
import dev.sweety.config.prop.PropConfiguration;
import dev.sweety.config.toml.TomlConfiguration;
import dev.sweety.config.xml.XmlConfiguration;
import dev.sweety.config.yml.YamlConfiguration;

import javax.sql.rowset.spi.XmlReader;
import java.beans.XMLEncoder;
import java.nio.file.Path;
import java.util.*;

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

        Map<String, Object> map = tree();

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

        public TestObj(Map<String, Object> me) {
            this.name = getAs(me, "name");
            this.age = getAs(me, "age", Integer.class);
            this.salary = getAs(me, "salary", Float.class);
            this.height = getAs(me, "height", Double.class);
            this.birthday = getAs(me, "birthday", Byte.class);
            this.gender = getAs(me, "gender", Character.class);
            this.weight = getAs(me, "weight", Short.class);
            this.honor = getAs(me, "honor", Boolean.class);
            this.map = getAs(me, "map");
        }

        @Override
        public String toString() {
            return "obj(%s, %d, %f, %f, %d)".formatted(name, age, salary, height, birthday);
        }

        @Override
        public Map<String, Object> serialize() {
            final Map<String, Object> me = tree();
            me.put("name", name);
            me.put("age", age);
            me.put("salary", salary);
            me.put("height", height);
            me.put("birthday", birthday);
            me.put("gender", gender);
            me.put("weight", weight);
            me.put("honor", honor);
            me.put("map", map);
            return me;
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
