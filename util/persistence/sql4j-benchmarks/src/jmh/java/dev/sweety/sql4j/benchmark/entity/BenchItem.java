package dev.sweety.sql4j.benchmark.entity;

import dev.sweety.sql4j.api.annotation.Cacheable;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

/**
 * Minimal entity used across all SQL4J benchmarks.
 * Mapped to {@code bench_items} (created once per benchmark state, dropped on teardown).
 */
@Table.Info(name = "bench_items")
@Cacheable(maxSize = 10_000)
public class BenchItem {

    @Column.Info(name = "id", primaryKey = true, autoIncrement = true)
    private Integer id;

    @Column.Info(name = "name")
    private String name;

    @Column.Info(name = "score")
    private int value;

    public BenchItem() {}

    public BenchItem(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public Integer getId()       { return id; }
    public void setId(Integer id){ this.id = id; }
    public String getName()      { return name; }
    public void setName(String n){ this.name = n; }
    public int getValue()        { return value; }
    public void setValue(int v)  { this.value = v; }
}
