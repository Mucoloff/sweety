package dev.sweety.project.sql4j;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table.Info(name = "products")
public class Product {

    @Column.Info(primaryKey = true, autoIncrement = true, name = "id")
    private long id;

    @Column.Info(name = "name")
    private String name;

    @Column.Info(name = "price")
    private float price;

    @Column.Info(name = "stock_quantity")
    private int stockQuantity;

    public Product(String name, float price, int stockQuantity) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }
}
