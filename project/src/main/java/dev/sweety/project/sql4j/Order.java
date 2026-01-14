package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.ForeignKey;
import dev.sweety.sql4j.api.obj.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table.Info(name = "orders")
public class Order {

    @Column.Info(primaryKey = true, autoIncrement = true, name = "id")
    private long id;

    @Column.Info(name = "user_id")
    @ForeignKey.Info(table = User.class)
    private long userId;

    @Column.Info(name = "status")
    private String status;

    public Order(long userId, String status) {
        this.userId = userId;
        this.status = status;
    }
}
