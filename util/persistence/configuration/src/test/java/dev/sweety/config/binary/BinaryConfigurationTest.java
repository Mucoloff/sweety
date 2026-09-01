package dev.sweety.config.binary;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BinaryConfigurationTest {

    @Test
    public void testRoundTripV2() throws IOException {
        BinaryConfiguration config = new BinaryConfiguration();

        Map<String, Object> data = new TreeMap<>();
        data.put("app.name", "Sweety Framework");
        data.put("server.port", 8080);
        data.put("server.negativeInt", -4242);
        data.put("server.negativeLong", -9876543210L);
        data.put("server.enabled", true);
        data.put("server.debug", false);
        data.put("metrics.rate", 99.95);
        data.put("cluster.session", UUID.randomUUID());

        List<Object> tags = new ArrayList<>();
        tags.add("netty");
        tags.add(100);
        tags.add(true);
        tags.add(false);
        data.put("server.tags", tags);

        Map<String, Object> nested = new HashMap<>();
        nested.put("key1", "value1");
        nested.put("count", 5);
        data.put("server.nested", nested);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        config.dumpToStream(data, out);

        byte[] serialized = out.toByteArray();
        assertTrue(serialized.length > 5);
        assertEquals('C', (char) serialized[0]);
        assertEquals('F', (char) serialized[1]);
        assertEquals('G', (char) serialized[2]);
        assertEquals('2', (char) serialized[3]);
        assertEquals(2, serialized[4]);

        ByteArrayInputStream in = new ByteArrayInputStream(serialized);
        Map<String, Object> loaded = config.loadFromStream(in);

        assertEquals("Sweety Framework", loaded.get("app.name"));
        assertEquals(8080, loaded.get("server.port"));
        assertEquals(-4242, loaded.get("server.negativeInt"));
        assertEquals(-9876543210L, loaded.get("server.negativeLong"));
        assertEquals(true, loaded.get("server.enabled"));
        assertEquals(false, loaded.get("server.debug"));
        assertEquals(99.95, (Double) loaded.get("metrics.rate"), 0.001);
        assertEquals(data.get("cluster.session"), loaded.get("cluster.session"));

        assertInstanceOf(List.class, loaded.get("server.tags"));
        List<?> loadedTags = (List<?>) loaded.get("server.tags");
        assertEquals("netty", loadedTags.get(0));
        assertEquals(100, loadedTags.get(1));
        assertEquals(true, loadedTags.get(2));
        assertEquals(false, loadedTags.get(3));

        assertInstanceOf(Map.class, loaded.get("server.nested"));
        Map<?, ?> loadedNested = (Map<?, ?>) loaded.get("server.nested");
        assertEquals("value1", loadedNested.get("key1"));
        assertEquals(5, loadedNested.get("count"));
    }

    @Test
    public void testLegacyV1BackwardCompatibility() throws IOException {
        BinaryConfiguration config = new BinaryConfiguration();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeBytes("CFG1");
        dos.writeByte(1);

        // write legacy map with 1 entry: "legacyKey" -> 1234
        dos.writeByte(7); // Map tag
        dos.writeInt(1);  // size
        dos.writeUTF("legacyKey");
        dos.writeByte(2); // Int tag
        dos.writeInt(1234);
        dos.flush();

        byte[] legacyBytes = baos.toByteArray();
        ByteArrayInputStream in = new ByteArrayInputStream(legacyBytes);

        Map<String, Object> loaded = config.loadFromStream(in);
        assertNotNull(loaded);
        assertEquals(1234, loaded.get("legacyKey"));
    }

    @Test
    public void testCycleDetection() {
        BinaryConfiguration config = new BinaryConfiguration();
        Map<String, Object> map1 = new HashMap<>();
        Map<String, Object> map2 = new HashMap<>();
        map1.put("loop", map2);
        map2.put("back", map1);

        assertThrows(RuntimeException.class, () -> {
            config.dumpToStream(map1, new ByteArrayOutputStream());
        });
    }
}
