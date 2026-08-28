package dev.sweety.sql4j.api;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.TableAccessor;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.impl.BaseRepository;
import dev.sweety.sql4j.impl.connection.ConnectionType;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.thread.ThreadUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SQL4JPrimitiveBoxingTest {

    private SqlConnection connection;
    private String dbName;

    @Table.Info(name = "primitive_entities")
    public static class PrimitiveEntity {
        @Column.Info(name = "id", primaryKey = true)
        private int id;

        @Column.Info(name = "flag")
        private boolean flag;

        @Column.Info(name = "byte_val")
        private byte byteVal;

        @Column.Info(name = "short_val")
        private short shortVal;

        @Column.Info(name = "long_val")
        private long longVal;

        @Column.Info(name = "float_val")
        private float floatVal;

        @Column.Info(name = "double_val")
        private double doubleVal;

        @Column.Info(name = "char_val")
        private char charVal;

        @Column.Info(name = "str_val")
        private String strVal;

        public PrimitiveEntity() {}

        public PrimitiveEntity(int id, boolean flag, byte byteVal, short shortVal, long longVal, float floatVal, double doubleVal, char charVal, String strVal) {
            this.id = id;
            this.flag = flag;
            this.byteVal = byteVal;
            this.shortVal = shortVal;
            this.longVal = longVal;
            this.floatVal = floatVal;
            this.doubleVal = doubleVal;
            this.charVal = charVal;
            this.strVal = strVal;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public boolean isFlag() { return flag; }
        public void setFlag(boolean flag) { this.flag = flag; }

        public byte getByteVal() { return byteVal; }
        public void setByteVal(byte byteVal) { this.byteVal = byteVal; }

        public short getShortVal() { return shortVal; }
        public void setShortVal(short shortVal) { this.shortVal = shortVal; }

        public long getLongVal() { return longVal; }
        public void setLongVal(long longVal) { this.longVal = longVal; }

        public float getFloatVal() { return floatVal; }
        public void setFloatVal(float floatVal) { this.floatVal = floatVal; }

        public double getDoubleVal() { return doubleVal; }
        public void setDoubleVal(double doubleVal) { this.doubleVal = doubleVal; }

        public char getCharVal() { return charVal; }
        public void setCharVal(char charVal) { this.charVal = charVal; }

        public String getStrVal() { return strVal; }
        public void setStrVal(String strVal) { this.strVal = strVal; }
    }

    @BeforeEach
    public void setUp() {
        dbName = "primitive_test_" + System.nanoTime() + ".db";
        connection = ConnectionType.SQLITE.create(ThreadUtil.singleThreadScheduler("primitive-test"), dbName);
    }

    @AfterEach
    public void tearDown() {
        new File(dbName).delete();
    }

    @Test
    public void testAllPrimitivesCrudAndZeroBoxingHydration() {
        TableRegistry registry = TableRegistry.getDefault();
        Table<PrimitiveEntity> table = registry.get(PrimitiveEntity.class);
        assertNotNull(table);

        TableAccessor<PrimitiveEntity> accessor = (TableAccessor<PrimitiveEntity>) table.accessor();
        assertNotNull(accessor);

        BaseRepository<PrimitiveEntity> repo = new BaseRepository<>(table, connection.dialect(), new QueryCache(), registry, null);
        connection.executeAsync(repo.createTable()).join();

        // Insert entity with all 8 primitive types
        PrimitiveEntity entity = new PrimitiveEntity(1, true, (byte) 12, (short) 1234, 9876543210L, 3.14f, 2.718281828, 'Z', "Hello Primitive!");
        connection.executeAsync(repo.insert(entity)).join();

        // Select entity back and verify all 8 primitive values match exactly
        List<PrimitiveEntity> result = connection.executeAsync(repo.selectWhere("id = ?", 1)).join();
        assertNotNull(result);
        assertEquals(1, result.size());

        PrimitiveEntity loaded = result.get(0);
        assertEquals(1, loaded.getId());
        assertTrue(loaded.isFlag());
        assertEquals((byte) 12, loaded.getByteVal());
        assertEquals((short) 1234, loaded.getShortVal());
        assertEquals(9876543210L, loaded.getLongVal());
        assertEquals(3.14f, loaded.getFloatVal(), 0.0001f);
        assertEquals(2.718281828, loaded.getDoubleVal(), 0.000000001);
        assertEquals('Z', loaded.getCharVal());
        assertEquals("Hello Primitive!", loaded.getStrVal());
    }

    @Test
    public void testMismatchedPrimitiveThrowsUnsupportedOperationException() {
        TableRegistry registry = TableRegistry.getDefault();
        Table<PrimitiveEntity> table = registry.get(PrimitiveEntity.class);
        assertNotNull(table);

        TableAccessor<PrimitiveEntity> accessor = (TableAccessor<PrimitiveEntity>) table.accessor();
        assertNotNull(accessor);

        PrimitiveEntity entity = new PrimitiveEntity();

        // colIndex 0 is 'id' (INT), colIndex 1 is 'flag' (BOOLEAN)
        // Calling getBoolean on colIndex 0 must throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> accessor.getBoolean(entity, 0));

        // Calling getInt on colIndex 1 must throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> accessor.getInt(entity, 1));

        // Calling getDouble on colIndex 2 ('byte_val') must throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> accessor.getDouble(entity, 2));

        // Calling setLong on colIndex 1 ('flag') must throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> accessor.setLong(entity, 1, 100L));
    }
}
