package dev.sweety.sql4j.it.entity;

import dev.sweety.sql4j.api.annotation.Cacheable;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.SoftDelete;

import java.util.Objects;

@Table.Info(name = "it_items")
@Cacheable(maxSize = 50)
public class ItemIT {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "name")
    private String name;

    @Column.Info(name = "deleted")
    @SoftDelete
    private boolean deleted;

    public ItemIT() {}

    public ItemIT(String name) {
        this.name = name;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemIT other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() { return Objects.hash(id, name); }

    @Override
    public String toString() { return "ItemIT{id=" + id + ", name='" + name + "'}"; }
}
