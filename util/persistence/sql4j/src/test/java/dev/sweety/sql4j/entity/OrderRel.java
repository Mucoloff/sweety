package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;

@Table.Info(name = "orders_rel")
public class OrderRel {
    @Column.Info(primaryKey = true, autoIncrement = true)
    private int id;
    
    @Column.Info
    private String product;
    
    @ManyToOne
    private UserRel user;

    public OrderRel() {}
    public OrderRel(String product, UserRel user) {
        this.product = product;
        this.user = user;
    }
    public int getId() { return id; }
    public String getProduct() { return product; }
    public UserRel getUser() { return user; }
}
