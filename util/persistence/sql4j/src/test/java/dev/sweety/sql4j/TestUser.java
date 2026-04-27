package dev.sweety.sql4j;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

@Table.Info(name = "users")
public class TestUser {

    @Column.Info(primaryKey = true, autoIncrement = true)
    private int id;

    @Column.Info
    private String name;

    @Column.Info
    private int age;

    public TestUser() {}

    public TestUser(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
}
