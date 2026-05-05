package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.ManyToMany;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;
import java.util.List;

@Table.Info(name = "users_rel")
public class UserRel {
    @Column.Info(primaryKey = true, autoIncrement = true)
    private int id;
    
    @Column.Info
    private String name;
    
    @OneToMany(mappedBy = "user")
    private List<OrderRel> orders;
    
    @ManyToMany(joinTable = "users_roles")
    private List<RoleRel> roles;

    public UserRel() {}
    public UserRel(String name) { this.name = name; }
    public int getId() { return id; }
    public String getName() { return name; }
    public List<OrderRel> getOrders() { return orders; }
    public void setOrders(List<OrderRel> orders) { this.orders = orders; }
    public List<RoleRel> getRoles() { return roles; }
    public void setRoles(List<RoleRel> roles) { this.roles = roles; }
}
