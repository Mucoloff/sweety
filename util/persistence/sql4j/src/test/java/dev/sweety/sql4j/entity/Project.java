package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;

import java.util.ArrayList;
import java.util.List;

@Table.Info(name = "full_projects")
public class Project {
    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "title", nullable = false)
    private String title;

    @ManyToOne(columnName = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "project_id")
    private List<Task> tasks = new ArrayList<>();

    public Project() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public List<Task> getTasks() { return tasks; }
}
