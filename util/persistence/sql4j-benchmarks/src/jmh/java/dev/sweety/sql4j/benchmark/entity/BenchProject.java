package dev.sweety.sql4j.benchmark.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.ManyToOne;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;

import java.util.ArrayList;
import java.util.List;

@Table.Info(name = "bench_projects")
public class BenchProject {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "title")
    private String title;

    @ManyToOne(columnName = "owner_id")
    private BenchUser owner;

    @OneToMany(mappedBy = "project_id")
    private final List<BenchTask> tasks = new ArrayList<>();

    public BenchProject() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BenchUser getOwner() { return owner; }
    public void setOwner(BenchUser owner) { this.owner = owner; }
    public List<BenchTask> getTasks() { return tasks; }
}
