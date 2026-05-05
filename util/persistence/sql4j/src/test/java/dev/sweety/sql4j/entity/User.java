package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Table.Info(name = "full_users")
public class User {
    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "name", nullable = false)
    @Unique
    private String name;

    @Column.Info(name = "age")
    @Index
    private int age;

    @Column.Info(name = "role")
    private String role;

    @Column.Info(name = "is_deleted")
    @SoftDelete
    private int deleted;

    @OneToMany(mappedBy = "owner_id")
    private List<Project> projects = new ArrayList<>();

    public User() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getDeleted() { return deleted; }
    public void setDeleted(int deleted) { this.deleted = deleted; }
    public List<Project> getProjects() { return projects; }
}
