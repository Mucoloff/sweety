package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

@Table.Info(name = "upsert_data")
public class TestUpsertData {

    @Column.Info(primaryKey = true) // NO autoIncrement!
    private String id;

    @Column.Info
    private String dataValue;

    public TestUpsertData() {}

    public TestUpsertData(String id, String dataValue) {
        this.id = id;
        this.dataValue = dataValue;
    }

    public String getId() { return id; }
    public String getDataValue() { return dataValue; }
    public void setDataValue(String dataValue) { this.dataValue = dataValue; }
}
