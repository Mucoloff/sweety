package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.Index;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;
import dev.sweety.sql4j.api.obj.annotation.SoftDelete;
import dev.sweety.sql4j.api.obj.annotation.Unique;
import dev.sweety.sql4j.api.annotation.Cacheable;

import java.util.ArrayList;
import java.util.List;

@Table.Info(name = "full_users")
@Cacheable(maxSize = 100)
public class User {
    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "name")
    @Unique
    private String name;

    @Column.Info(name = "age")
    @Index
    private int age;

    @Column.Info(name = "role")
    private Role role;

    @Column.Info(name = "is_deleted")
    @SoftDelete
    private boolean deleted;

    @OneToMany(mappedBy = "owner_id")
    private final List<Project> projects = new ArrayList<>();

    public User() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public List<Project> getProjects() {
        return projects;
    }
}
