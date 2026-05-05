package dev.sweety.sql4j.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.*;

@Table.Info(name = "full_tasks")
public class Task {
    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "description", nullable = false)
    private String description;

    @Column.Info(name = "status")
    private String status;

    @ManyToOne(columnName = "project_id")
    private Project project;

    public Task() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
}
