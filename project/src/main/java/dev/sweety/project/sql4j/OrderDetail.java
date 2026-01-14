package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table.Info(name = "order_details")
public class OrderDetail {

    @Column.Info(primaryKey = true, name = "order_id")
    private long orderId;

    @Column.Info(primaryKey = true, name = "product_id")
    private long productId;

    @Column.Info(name = "quantity")
    private int quantity;

    public OrderDetail(long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

}
