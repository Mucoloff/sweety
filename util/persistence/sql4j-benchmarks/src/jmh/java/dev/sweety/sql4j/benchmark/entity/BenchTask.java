package dev.sweety.sql4j.benchmark.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;

@Table.Info(name = "bench_tasks")
public class BenchTask {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "description")
    private String description;

    @ManyToOne(columnName = "project_id")
    private BenchProject project;

    public BenchTask() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BenchProject getProject() { return project; }
    public void setProject(BenchProject project) { this.project = project; }
}
