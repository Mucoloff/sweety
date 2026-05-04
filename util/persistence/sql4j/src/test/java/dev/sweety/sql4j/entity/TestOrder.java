package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.ForeignKey;
import dev.sweety.sql4j.api.obj.Table;

import java.util.StringJoiner;

@Table.Info(name = "orders")
public class TestOrder {
    @Column.Info(primaryKey = true, autoIncrement = true)
    private int id;

    @Column.Info
    @ForeignKey.Info(table = TestUser.class, column = "id",
            onDelete = ForeignKey.Action.CASCADE, onUpdate = ForeignKey.Action.CASCADE)
    private int userId;

    @Column.Info
    @ForeignKey.Info(table = TestData.class, column = "id",
            onDelete = ForeignKey.Action.CASCADE, onUpdate = ForeignKey.Action.CASCADE)
    private int dataId;

    public TestOrder() {}

    public TestOrder(TestUser user, TestData data) {
        this.userId = user.getId();
        this.dataId = data.getId();
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public int getDataId() { return dataId; }

    @Override
    public String toString() {
        return new StringJoiner(", ", TestOrder.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("userId=" + userId)
                .add("dataId=" + dataId)
                .toString();
    }
}
