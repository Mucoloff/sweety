package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

@Table.Info(name = "roles_rel")
public class RoleRel {
    @Column.Info(primaryKey = true, autoIncrement = true)
    private int id;
    
    @Column.Info
    private String name;

    public RoleRel() {}
    public RoleRel(String name) { this.name = name; }
    public int getId() { return id; }
    public String getName() { return name; }
}
