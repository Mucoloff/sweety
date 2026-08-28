package dev.sweety.sql4j.benchmark.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.OneToMany;

import java.util.ArrayList;
import java.util.List;

/**
 * Owner side of the {@link BenchProject}/{@link BenchTask} relation chain used by
 * {@link dev.sweety.sql4j.benchmark.RelationJoinBenchmark} to compare EAGER join hydration
 * ({@code SelectJoin.mapToHierarchy}) against a LAZY-relation N+1 access pattern.
 */
@Table.Info(name = "bench_users")
public class BenchUser {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "name")
    private String name;

    @OneToMany(mappedBy = "owner_id")
    private final List<BenchProject> projects = new ArrayList<>();

    public BenchUser() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<BenchProject> getProjects() { return projects; }
}
